package org.vfeeg.eegfaktura.billing.service;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
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
 * nothing expires early and nothing stays behind (the mail run releases its session locks itself). Nobody waits: a conflicting request is refused with 409.
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
@Slf4j
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
     * one and may take minutes, so the locks are session locks on a connection of its own (autocommit, never idle in
     * a transaction), which the mail run does not use, released explicitly afterwards. A failure while releasing or
     * closing is logged and never replaces the mail run's result or exception; a connection whose locks could not be
     * released is evicted from the pool, which ends its session and with it the locks.
     */
    public <T> T mailRun(UUID billingRunId, Action<T> action) throws IOException {
        Connection connection;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            throw new DataAccessResourceFailureException("Sperre des Abrechnungslaufs nicht möglich", e);
        }
        boolean mailLocked = false;
        boolean runLocked = false;
        try {
            mailLocked = tryLock(connection, "pg_try_advisory_lock", mailKey(billingRunId));
            runLocked = mailLocked && tryLock(connection, "pg_try_advisory_lock_shared", runKey(billingRunId));
            if (!runLocked) {
                throw new BusyException();
            }
            return action.run();
        } finally {
            boolean released = !runLocked || unlock(connection, "pg_advisory_unlock_shared", billingRunId);
            released &= !mailLocked || unlock(connection, "pg_advisory_unlock", billingRunId);
            close(connection, released, billingRunId);
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

    /** Releases one session lock of the mail run; false (logged) when that failed. */
    private static boolean unlock(Connection connection, String unlockFunction, UUID billingRunId) {
        String key = unlockFunction.endsWith("_shared") ? runKey(billingRunId) : mailKey(billingRunId);
        try (PreparedStatement statement = connection.prepareStatement(
                "select " + unlockFunction + "(?, hashtext(?))")) {
            statement.setInt(1, LOCK_CLASS);
            statement.setString(2, key);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next() && result.getBoolean(1)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // falls through: logged below
        }
        log.warn("Sperre des Mailversands nicht freigegeben ({}, billingRunId={})", unlockFunction, billingRunId);
        return false;
    }

    /** Returns the mail run's connection to the pool; without released locks it is evicted first. */
    private void close(Connection connection, boolean released, UUID billingRunId) {
        try {
            if (!released && dataSource.isWrapperFor(HikariDataSource.class)) {
                dataSource.unwrap(HikariDataSource.class).evictConnection(connection);
            }
        } catch (Exception e) {
            log.warn("Verbindung des Mailversands nicht verworfen (billingRunId={})", billingRunId);
        }
        try {
            connection.close();
        } catch (Exception e) {
            log.warn("Verbindung des Mailversands nicht geschlossen (billingRunId={})", billingRunId);
        }
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
