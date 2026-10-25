package org.vfeeg.eegfaktura.billing.rest;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;
import org.vfeeg.eegfaktura.billing.service.BillingRunLauncher;
import org.vfeeg.eegfaktura.billing.service.BillingService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.vfeeg.eegfaktura.billing.rest.Endpoint.ID;
import static org.vfeeg.eegfaktura.billing.rest.TestTokens.OWN;

/** {@code POST /api/billing}: the billing run (tenant in the body; since 1.0.8 accepted with 202, claimed, launched). */
@WebMvcTest(controllers = BillingResource.class)
class BillingWebTests extends EndpointMatrix {

    @MockitoBean
    BillingService billingService;
    @MockitoBean
    BillingRunLauncher billingRunLauncher;

    @Override
    protected List<Endpoint> endpoints() {
        return EndpointTable.of(BillingResource.class);
    }

    @Override
    protected Object[] services() {
        return new Object[]{billingService};
    }

    @Override
    protected void stubRecordsOf(String tenant) {
        BillingRun run = new BillingRun();
        run.setId(ID);
        run.setRunStatus(BillingRunStatus.NEW);
        when(billingService.acceptBillingRun(any())).thenReturn(run);
        when(billingService.claimBillingRun(ID)).thenReturn(true);
    }

    @Override
    protected void stubUnknownIds() {
        // no id endpoint
    }

    @Test
    void runOfTheOwnTenantReturnsTheResults() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(POST, "/api/billing").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.billingRunId").value(ID.toString()))
                .andExpect(jsonPath("$.abstractText").value("Abrechnungslauf gestartet."));
        verify(billingService).acceptBillingRun(argThat(p -> OWN.equals(p.getTenantId())
                && "YQ-2024-1".equals(p.getClearingPeriodIdentifier()) && p.isPreview()));
        verify(billingRunLauncher).launch(eq(ID), argThat(p -> OWN.equals(p.getTenantId())));
    }

    /** A second start while the period runs: the claim is lost, 409 with the run's id, nothing is launched. */
    @Test
    void aRunningPeriodIsRefusedWithConflict() throws Exception {
        stubRecordsOf(OWN);
        when(billingService.claimBillingRun(ID)).thenReturn(false);
        mvc.perform(endpoint(POST, "/api/billing").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.billingRunId").value(ID.toString()));
        verify(billingRunLauncher, never()).launch(any(), any());
    }

    /**
     * Two first starts of one period at the same time: the second insert hits the unique index (V1_15) and is
     * answered as a running run (409), nothing is launched. Covered here deterministically; the concurrency
     * tests reach this path only when the race happens, which made the rest coverage gate flaky.
     */
    @Test
    void aSimultaneousFirstStartIsAConflict() throws Exception {
        stubRecordsOf(OWN);
        when(billingService.acceptBillingRun(any())).thenThrow(new DataIntegrityViolationException("uq_billing_run"));
        mvc.perform(endpoint(POST, "/api/billing").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isConflict());
        verify(billingRunLauncher, never()).launch(any(), any());
    }

    /** The executor is full: 503, and the claim goes back to the status before it. */
    @Test
    void aRejectedLaunchRevertsTheClaim() throws Exception {
        stubRecordsOf(OWN);
        doThrow(new TaskRejectedException("full")).when(billingRunLauncher).launch(eq(ID), any());
        mvc.perform(endpoint(POST, "/api/billing").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.billingRunId").value(ID.toString()));
        verify(billingService).revertBillingRunClaim(ID, BillingRunStatus.NEW);
    }

    /** A body without the community must be a validation error, not a refused tenant (500). */
    @Disabled("known-errors #31")
    @Test
    void bodyWithoutTenantIsBadRequestWithFieldErrors() throws Exception {
        mvc.perform(post("/api/billing").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clearingPeriodType\":\"YQ\",\"clearingPeriodIdentifier\":\"YQ-2024-1\"}")
                        .header("Tenant", OWN).header(HttpHeaders.AUTHORIZATION, TestTokens.admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("tenantId"));
        verify(billingService, never()).acceptBillingRun(any());
    }

    @Disabled("known-errors #31")
    @Test
    void unreadableJsonIsBadRequest() throws Exception {
        mvc.perform(post("/api/billing").contentType(MediaType.APPLICATION_JSON).content("{\"tenantId\":")
                        .header("Tenant", OWN).header(HttpHeaders.AUTHORIZATION, TestTokens.admin()))
                .andExpect(status().isBadRequest());
    }
}
