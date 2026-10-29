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
 * One export, mail run or deletion of a billing run at a time (known-errors #14): a PostgreSQL transaction-level
 * advisory lock per run, like {@link FinalRunLock} - across replicas, released by the database when the
 * transaction or its connection ends, so nothing expires early and nothing stays behind. A second action on the
 * same run does not wait: it is refused with 409. Replaces the per-tenant {@code InMemoryLockRepository}; actions on
 * different runs of a community no longer wait for each other.
 */
@Component
public class BillingRunActionLock {

    static final String BUSY_MESSAGE = "Für diesen Abrechnungslauf läuft gerade ein Export, ein Mailversand oder"
            + " das Löschen – bitte danach erneut versuchen.";
    /** first key of the two-key advisory lock; {@link FinalRunLock} uses 217 */
    private static final int LOCK_CLASS = 218;

    /** The locked action; may throw the IOException of an export. */
    @FunctionalInterface
    public interface Action<T> {
        T run() throws IOException;
    }

    private final DataSource dataSource;
    private final TransactionTemplate transactionTemplate;

    public BillingRunActionLock(final DataSource dataSource, final PlatformTransactionManager transactionManager) {
        this.dataSource = dataSource;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Runs the action in a transaction that holds the run's lock; the action's own transactions join it. */
    public <T> T inTransaction(UUID billingRunId, Action<T> action) throws IOException {
        try {
            return transactionTemplate.execute(status -> {
                lock(DataSourceUtils.getConnection(dataSource), billingRunId);
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

    /**
     * For an action that must not run in one transaction (the mail run commits its status steps one by one): the
     * lock is held in a transaction on a connection of its own, which the action does not use.
     */
    public <T> T onOwnConnection(UUID billingRunId, Action<T> action) throws IOException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                lock(connection, billingRunId);
                return action.run();
            } finally {
                connection.rollback(); // ends the transaction and with it the lock
            }
        } catch (SQLException e) {
            throw new DataAccessResourceFailureException("Sperre des Abrechnungslaufs nicht möglich", e);
        }
    }

    /** Takes the run's lock without waiting, or 409 when another action of the run holds it. */
    private static void lock(Connection connection, UUID billingRunId) {
        try (PreparedStatement statement = connection.prepareStatement(
                "select pg_try_advisory_xact_lock(?, hashtext(?))")) {
            statement.setInt(1, LOCK_CLASS);
            statement.setString(2, "billing_run_action/" + billingRunId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !result.getBoolean(1)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, BUSY_MESSAGE);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessResourceFailureException("Sperre des Abrechnungslaufs nicht möglich", e);
        }
    }
}
