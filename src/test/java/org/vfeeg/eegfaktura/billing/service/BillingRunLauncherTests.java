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
