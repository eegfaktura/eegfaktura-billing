package org.vfeeg.eegfaktura.billing.rest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentItemDTO;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentItemService;
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

@WebMvcTest(controllers = BillingDocumentItemResource.class)
class BillingDocumentItemWebTests extends EndpointMatrix {

    @MockitoBean
    BillingDocumentItemService billingDocumentItemService;

    @Override
    protected List<Endpoint> endpoints() {
        return EndpointTable.of(BillingDocumentItemResource.class);
    }

    @Override
    protected Object[] services() {
        return new Object[]{billingDocumentItemService};
    }

    @Override
    protected void stubRecordsOf(String tenant) {
        BillingDocumentItemDTO item = new BillingDocumentItemDTO();
        item.setId(ID);
        item.setTenantId(tenant);
        when(billingDocumentItemService.get(ID)).thenReturn(item);
    }

    @Override
    protected void stubUnknownIds() {
        when(billingDocumentItemService.get(any())).thenThrow(new NotFoundException());
    }

    @Test
    void itemOfTheOwnTenantIsReturned() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(GET, "/api/billingDocumentItems/{id}").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.tenantId").value(OWN));
    }

    @Test
    void deleteRemovesTheItem() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(DELETE, "/api/billingDocumentItems/{id}").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isNoContent());
        verify(billingDocumentItemService).delete(ID);
    }
}
