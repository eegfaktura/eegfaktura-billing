package org.vfeeg.eegfaktura.billing.service;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Export, mail run and deletion of a billing run (known-errors #14): PostgreSQL advisory locks per run, like
 * {@link FinalRunLock} - across replicas, released by the database when the transaction or its connection ends, so
 * nothing expires early and nothing stays behind. Nobody waits: a conflicting request is refused with 409.
 * <ul>
 *   <li>exports only read: they take the run key shared, so they run next to each other and next to a mail run;</li>
 *   <li>a deletion takes the run key exclusively: refused while an export or mail run of the run is busy, and
 *   these are refused while it deletes;</li>
 *   <li>a mail run takes the run key shared plus the run's mail key exclusively: a second mail run of the same run
 *   is refused.</li>
 * </ul>
 * Replaces the per-tenant {@code InMemoryLockRepository}; requests for different runs never wait for each other.
 */
@Component
public class BillingRunActionLock {

    public static final String BUSY_MESSAGE = "Dieser Abrechnungslauf wird gerade bearbeitet (Export, Mailversand"
            + " oder Löschen) – bitte danach erneut versuchen.";
    /** first key of the two-key advisory lock; {@link FinalRunLock} uses 217 */
    private static final int LOCK_CLASS = 218;

    /** The locked action; may throw the IOException of an export. */
    @FunctionalInterface
    public interface Action<T> {
        T run() throws IOException;
    }

    /** 409: a conflicting action of the run is busy. */
    public static class BusyException extends ResponseStatusException {
        public BusyException() { super(HttpStatus.CONFLICT, BUSY_MESSAGE); }
    }

    private final DataSource dataSource;
    private final TransactionTemplate transactionTemplate;

    public BillingRunActionLock(final DataSource dataSource, final PlatformTransactionManager transactionManager) {
        this.dataSource = dataSource;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** xlsx/archive export: run key shared, in a transaction that the export's own calls join. */
    public <T> T export(UUID billingRunId, Action<T> action) throws IOException {
        return inTransaction("pg_try_advisory_xact_lock_shared", billingRunId, action);
    }

    /** Deletion: run key exclusive, in a transaction that the deletion's own calls join. */
    public <T> T delete(UUID billingRunId, Action<T> action) throws IOException {
        return inTransaction("pg_try_advisory_xact_lock", billingRunId, action);
    }

    /**
     * Mail run: the run's mail key exclusive and its run key shared. The mail run commits its status steps one by
     * one, so it does not run in one transaction: the locks are held on a connection of its own, which the mail run
     * does not use.
     */
    public <T> T mailRun(UUID billingRunId, Action<T> action) throws IOException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!tryLock(connection, "pg_try_advisory_xact_lock", mailKey(billingRunId))
                        || !tryLock(connection, "pg_try_advisory_xact_lock_shared", runKey(billingRunId))) {
                    throw new BusyException();
                }
                return action.run();
            } finally {
                connection.rollback(); // ends the transaction and with it the locks
            }
        } catch (SQLException e) {
            throw new DataAccessResourceFailureException("Sperre des Abrechnungslaufs nicht möglich", e);
        }
    }

    private <T> T inTransaction(String lockFunction, UUID billingRunId, Action<T> action) throws IOException {
        try {
            return transactionTemplate.execute(status -> {
                if (!tryLock(DataSourceUtils.getConnection(dataSource), lockFunction, runKey(billingRunId))) {
                    throw new BusyException();
                }
                try {
                    return action.run();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static String runKey(UUID billingRunId) {
        return "billing_run_action/" + billingRunId;
    }

    private static String mailKey(UUID billingRunId) {
        return "billing_run_mail/" + billingRunId;
    }

    /** Takes one advisory lock without waiting; {@code lockFunction} is one of the fixed names above. */
    private static boolean tryLock(Connection connection, String lockFunction, String key) {
        try (PreparedStatement statement = connection.prepareStatement(
                "select " + lockFunction + "(?, hashtext(?))")) {
            statement.setInt(1, LOCK_CLASS);
            statement.setString(2, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        } catch (SQLException e) {
            throw new DataAccessResourceFailureException("Sperre des Abrechnungslaufs nicht möglich", e);
        }
    }
}
