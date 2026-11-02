package org.vfeeg.eegfaktura.billing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Review finding on #14: releasing the mail run's session locks or closing its connection can fail (session killed,
 * network). That is logged and never replaces the mail run's result - the mails went out - nor its exception.
 */
class BillingRunActionLockReleaseTests {

    private static final UUID RUN = UUID.fromString("00000000-0000-0000-0000-0000000000cd");

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final BillingRunActionLock lock = new BillingRunActionLock(dataSource, mock(PlatformTransactionManager.class));

    @BeforeEach
    void connectionWhoseLocksCanBeTakenButNotReleased() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        PreparedStatement taken = mock(PreparedStatement.class);
        ResultSet granted = mock(ResultSet.class);
        when(granted.next()).thenReturn(true);
        when(granted.getBoolean(1)).thenReturn(true);
        when(taken.executeQuery()).thenReturn(granted);
        when(connection.prepareStatement(startsWith("select pg_try_advisory_lock"))).thenReturn(taken);
        when(connection.prepareStatement(startsWith("select pg_advisory_unlock")))
                .thenThrow(new SQLException("terminating connection due to administrator command"));
        doThrow(new SQLException("connection closed")).when(connection).close();
        doThrow(new SQLException("connection closed")).when(connection).rollback();
    }

    @Test
    void aFailingUnlockAndCloseDoNotChangeTheProtocol() throws Exception {
        assertThat(lock.mailRun(RUN, () -> "Send mail finished"), is("Send mail finished"));
        verify(connection).close();
    }

    @Test
    void aFailingUnlockAndCloseDoNotReplaceTheMailRunsException() {
        IllegalStateException failed = new IllegalStateException("mail run failed");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> lock.mailRun(RUN, () -> { throw failed; }));

        assertThat(thrown, is(sameInstance(failed)));
    }
}
