package org.vfeeg.eegfaktura.billing.rest;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.repos.InMemoryLockRepository;
import org.vfeeg.eegfaktura.billing.service.BillingService;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.vfeeg.eegfaktura.billing.rest.Endpoint.ID;
import static org.vfeeg.eegfaktura.billing.rest.TestTokens.OWN;

/** {@code POST /api/billing}: the billing run (tenant in the body, per-tenant lock). */
@WebMvcTest(controllers = BillingResource.class)
class BillingWebTests extends EndpointMatrix {

    @MockitoBean
    BillingService billingService;
    @Autowired
    InMemoryLockRepository lockRepository;

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
        DoBillingResults results = new DoBillingResults();
        results.setBillingRunId(ID);
        results.setAbstractText("2 invoices");
        when(billingService.doBilling(any())).thenReturn(results);
    }

    @Override
    protected void stubUnknownIds() {
        // no id endpoint
    }

    @Test
    void runOfTheOwnTenantReturnsTheResults() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(POST, "/api/billing").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billingRunId").value(ID.toString()))
                .andExpect(jsonPath("$.abstractText").value("2 invoices"));
        verify(billingService).doBilling(argThat(p -> OWN.equals(p.getTenantId())
                && "YQ-2024-1".equals(p.getClearingPeriodIdentifier()) && p.isPreview()));
    }

    /** The lock of the tenant is released after the run, also when the run fails. */
    @Test
    void aFailedRunReleasesTheTenantLock() throws Exception {
        Object lockBefore = lockRepository.getLock(OWN);
        when(billingService.doBilling(any())).thenThrow(new IllegalStateException("run failed"));
        mvc.perform(endpoint(POST, "/api/billing").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isInternalServerError());
        assertThat(lockRepository.getLock(OWN), not(sameInstance(lockBefore)));
        lockRepository.releaseLock(OWN);
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
        verify(billingService, never()).doBilling(any());
    }

    @Disabled("known-errors #31")
    @Test
    void unreadableJsonIsBadRequest() throws Exception {
        mvc.perform(post("/api/billing").contentType(MediaType.APPLICATION_JSON).content("{\"tenantId\":")
                        .header("Tenant", OWN).header(HttpHeaders.AUTHORIZATION, TestTokens.admin()))
                .andExpect(status().isBadRequest());
    }
}
