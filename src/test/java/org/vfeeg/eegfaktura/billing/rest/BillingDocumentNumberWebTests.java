package org.vfeeg.eegfaktura.billing.rest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentNumberDTO;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentNumberService;
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

@WebMvcTest(controllers = BillingDocumentNumberResource.class)
class BillingDocumentNumberWebTests extends EndpointMatrix {

    @MockitoBean
    BillingDocumentNumberService billingDocumentNumberService;

    @Override
    protected List<Endpoint> endpoints() {
        return EndpointTable.of(BillingDocumentNumberResource.class);
    }

    @Override
    protected Object[] services() {
        return new Object[]{billingDocumentNumberService};
    }

    @Override
    protected void stubRecordsOf(String tenant) {
        BillingDocumentNumberDTO number = new BillingDocumentNumberDTO();
        number.setId(ID);
        number.setTenantId(tenant);
        when(billingDocumentNumberService.get(ID)).thenReturn(number);
    }

    @Override
    protected void stubUnknownIds() {
        when(billingDocumentNumberService.get(any())).thenThrow(new NotFoundException());
    }

    @Test
    void numberOfTheOwnTenantIsReturned() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(GET, "/api/billingDocumentNumbers/{id}").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.tenantId").value(OWN));
    }

    @Test
    void deleteRemovesTheNumber() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(DELETE, "/api/billingDocumentNumbers/{id}").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isNoContent());
        verify(billingDocumentNumberService).delete(ID);
    }
}
