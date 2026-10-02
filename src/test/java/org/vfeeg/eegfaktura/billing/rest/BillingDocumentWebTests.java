package org.vfeeg.eegfaktura.billing.rest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentService;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.vfeeg.eegfaktura.billing.rest.Endpoint.ID;
import static org.vfeeg.eegfaktura.billing.rest.TestTokens.OWN;

@WebMvcTest(controllers = BillingDocumentResource.class)
class BillingDocumentWebTests extends EndpointMatrix {

    @MockitoBean
    BillingDocumentService billingDocumentService;

    @Override
    protected List<Endpoint> endpoints() {
        return EndpointTable.of(BillingDocumentResource.class);
    }

    @Override
    protected Object[] services() {
        return new Object[]{billingDocumentService};
    }

    @Override
    protected void stubRecordsOf(String tenant) {
        when(billingDocumentService.get(ID)).thenReturn(BillingRunWebTests.document(tenant));
        when(billingDocumentService.findByTenantIdAndYear(tenant, 2024))
                .thenReturn(List.of(BillingRunWebTests.document(tenant)));
    }

    @Override
    protected void stubUnknownIds() {
        when(billingDocumentService.get(any())).thenThrow(new NotFoundException());
    }

    @Test
    void documentsOfAYearAreListedForTheOwnTenant() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(GET, "/api/billingDocuments/tenant/{id}/{year}").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tenantId").value(OWN))
                .andExpect(jsonPath("$[0].documentNumber").value("R202400001"));
    }

    @Test
    void deleteRemovesTheDocument() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(DELETE, "/api/billingDocuments/{id}").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isNoContent());
        verify(billingDocumentService).delete(ID);
    }
}
