package org.vfeeg.eegfaktura.billing.service;

import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;

import java.sql.SQLException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The launcher (1.0.8) runs the calculation and turns every failure into the run's FAILED status with a short
 * summary: a business message is passed on, a technical one (database, persistence, SQL, none) is replaced by a
 * generic text so that no SQL or schema detail reaches the client (security review).
 */
class BillingRunLauncherTests {

    private static final UUID RUN = UUID.fromString("00000000-0000-0000-0000-0000000000ab");
    private static final String TECHNICAL = "Abrechnung fehlgeschlagen: technischer Fehler - Details im Server-Log.";

    private final BillingService billingService = mock(BillingService.class);
    private final BillingRunLauncher launcher = new BillingRunLauncher(billingService);
    private final DoBillingParams params = new DoBillingParams();

    @Test
    void aSuccessfulRunIsNotMarkedFailed() {
        launcher.launch(RUN, params);

        verify(billingService).executeBillingRun(RUN, params);
        verify(billingService, never()).markBillingRunFailed(any(), any());
    }

    @Test
    void aBusinessFailureKeepsItsMessage() {
        doThrow(new IllegalArgumentException("More than 2 VAT rates not supported")).when(billingService).executeBillingRun(eq(RUN), any());

        launcher.launch(RUN, params);

        verify(billingService).markBillingRunFailed(RUN, "Abrechnung fehlgeschlagen: More than 2 VAT rates not supported");
    }

    @Test
    void aDatabaseFailureIsSummarisedGenerically() {
        doThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint")).when(billingService).executeBillingRun(eq(RUN), any());

        launcher.launch(RUN, params);

        verify(billingService).markBillingRunFailed(RUN, TECHNICAL);
    }

    /** A constraint error quotes the whole row: the ERROR line keeps the type and the frames, not the values. */
    @Test
    void rowValuesOfADatabaseFailureAreNotLogged() {
        doThrow(new DataIntegrityViolationException("could not execute statement",
                new SQLException("ERROR: null value in column \"iban\" violates not-null constraint\n"
                        + "  Detail: Failing row contains (anna@example.at, AT611904300234573201)")))
                .when(billingService).executeBillingRun(eq(RUN), any());
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(BillingRunLauncher.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            launcher.launch(RUN, params);
        } finally {
            logger.detachAppender(appender);
        }

        ch.qos.logback.classic.spi.ILoggingEvent line = appender.list.get(0);
        String all = line.getFormattedMessage() + " " + line.getKeyValuePairs() + " "
                + ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(line.getThrowableProxy());
        org.hamcrest.MatcherAssert.assertThat(all, org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("AT611904300234573201")));
        org.hamcrest.MatcherAssert.assertThat(all, org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("anna@example.at")));
        org.hamcrest.MatcherAssert.assertThat(all, org.hamcrest.Matchers.containsString("BillingRunLauncherTests"));
        org.hamcrest.MatcherAssert.assertThat(line.getKeyValuePairs().get(0).value,
                org.hamcrest.Matchers.is((Object) DataIntegrityViolationException.class.getName()));
    }

    @Test
    void aPersistenceFailureIsSummarisedGenerically() {
        doThrow(new PersistenceException("could not execute statement")).when(billingService).executeBillingRun(eq(RUN), any());

        launcher.launch(RUN, params);

        verify(billingService).markBillingRunFailed(RUN, TECHNICAL);
    }

    @Test
    void anSqlFailureIsSummarisedGenerically() {
        doAnswer(invocation -> { throw new SQLException("connection reset"); }).when(billingService).executeBillingRun(eq(RUN), any());

        launcher.launch(RUN, params);

        verify(billingService).markBillingRunFailed(RUN, TECHNICAL);
    }

    @Test
    void aFailureWithoutAMessageIsSummarisedGenerically() {
        doThrow(new IllegalStateException()).when(billingService).executeBillingRun(eq(RUN), any());

        launcher.launch(RUN, params);

        verify(billingService).markBillingRunFailed(RUN, TECHNICAL);
    }
}
