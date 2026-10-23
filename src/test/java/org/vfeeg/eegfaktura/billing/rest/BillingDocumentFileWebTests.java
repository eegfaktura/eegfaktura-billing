package org.vfeeg.eegfaktura.billing.rest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentFileDTO;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentFileService;
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

@WebMvcTest(controllers = BillingDocumentFileResource.class)
class BillingDocumentFileWebTests extends EndpointMatrix {

    @MockitoBean
    BillingDocumentFileService billingDocumentFileService;

    @Override
    protected List<Endpoint> endpoints() {
        return EndpointTable.of(BillingDocumentFileResource.class);
    }

    @Override
    protected Object[] services() {
        return new Object[]{billingDocumentFileService};
    }

    @Override
    protected void stubRecordsOf(String tenant) {
        when(billingDocumentFileService.get(ID)).thenReturn(file(tenant));
        when(billingDocumentFileService.findByTenantId(tenant)).thenReturn(List.of(file(tenant)));
    }

    @Override
    protected void stubUnknownIds() {
        when(billingDocumentFileService.get(any())).thenThrow(new NotFoundException());
    }

    private static BillingDocumentFileDTO file(String tenant) {
        BillingDocumentFileDTO file = new BillingDocumentFileDTO();
        file.setId(ID);
        file.setTenantId(tenant);
        file.setName("R202400001.pdf");
        file.setMimeType("application/pdf");
        return file;
    }

    @Test
    void filesOfTheOwnTenantAreListed() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(GET, "/api/billingDocumentFiles/tenant/{id}").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tenantId").value(OWN))
                .andExpect(jsonPath("$[0].name").value("R202400001.pdf"));
    }

    @Test
    void deleteRemovesTheFile() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(DELETE, "/api/billingDocumentFiles/{id}").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isNoContent());
        verify(billingDocumentFileService).delete(ID);
    }
}
