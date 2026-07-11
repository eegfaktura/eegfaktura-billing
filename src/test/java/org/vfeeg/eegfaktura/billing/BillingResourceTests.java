package org.vfeeg.eegfaktura.billing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.rest.BillingResource;
import org.vfeeg.eegfaktura.billing.security.Authority;
import org.vfeeg.eegfaktura.billing.security.TenantContext;
import org.vfeeg.eegfaktura.billing.service.BillingRunLauncher;
import org.vfeeg.eegfaktura.billing.service.BillingService;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * HTTP-Semantik des asynchronen Starts: 202 (angenommen), 409 (laeuft
 * bereits), 503 + Claim-Rollback (Executor voll). Reiner Unit-Test mit
 * gemocktem Service/Launcher.
 */
class BillingResourceTests {

    private final BillingService billingService = mock(BillingService.class);
    private final BillingRunLauncher launcher = mock(BillingRunLauncher.class);
    private final BillingResource resource = new BillingResource(billingService, launcher);

    private final UUID runId = UUID.randomUUID();
    private BillingRun run;
    private DoBillingParams params;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(new Authority("TE100100"));
        run = BillingRun.builder().id(runId).runStatus(BillingRunStatus.NEW).build();
        params = new DoBillingParams();
        params.setTenantId("TE100100");
        when(billingService.acceptBillingRun(any())).thenReturn(run);
    }

    @AfterEach
    void tearDown() {
        TenantContext.setCurrentTenant(null);
    }

    @Test
    void acceptedRunReturns202WithRunId() {
        when(billingService.claimBillingRun(runId)).thenReturn(true);

        ResponseEntity<DoBillingResults> response = resource.startBilling(params);

        assertThat(response.getStatusCode(), equalTo(HttpStatus.ACCEPTED));
        assertThat(response.getBody().getBillingRunId(), equalTo(runId));
        verify(launcher).launch(runId, params);
    }

    @Test
    void alreadyRunningReturns409WithRunId() {
        when(billingService.claimBillingRun(runId)).thenReturn(false);

        ResponseEntity<DoBillingResults> response = resource.startBilling(params);

        assertThat(response.getStatusCode(), equalTo(HttpStatus.CONFLICT));
        assertThat(response.getBody().getBillingRunId(), equalTo(runId));
        verify(launcher, never()).launch(any(), any());
    }

    @Test
    void fullExecutorReturns503AndRevertsClaim() {
        run.setRunStatus(BillingRunStatus.FAILED); // Status vor dem Claim
        when(billingService.claimBillingRun(runId)).thenReturn(true);
        doThrow(new TaskRejectedException("full")).when(launcher).launch(any(), any());

        ResponseEntity<DoBillingResults> response = resource.startBilling(params);

        assertThat(response.getStatusCode(), equalTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(billingService).revertBillingRunClaim(runId, BillingRunStatus.FAILED);
    }

    @Test
    void closedRunReturns409() {
        when(billingService.acceptBillingRun(any()))
                .thenThrow(new BillingService.BillingRunAlreadyClosedException("bereits abgeschlossen"));

        ResponseEntity<DoBillingResults> response = resource.startBilling(params);

        assertThat(response.getStatusCode(), equalTo(HttpStatus.CONFLICT));
        assertThat(response.getBody().getAbstractText(), containsString("abgeschlossen"));
    }

    @Test
    void invalidDocumentDateReturns400() {
        when(billingService.acceptBillingRun(any()))
                .thenThrow(new IllegalArgumentException("Ungültiges Belegdatum"));

        ResponseEntity<DoBillingResults> response = resource.startBilling(params);

        assertThat(response.getStatusCode(), equalTo(HttpStatus.BAD_REQUEST));
    }
}
