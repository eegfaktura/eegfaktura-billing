package org.vfeeg.eegfaktura.billing.rest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.vfeeg.eegfaktura.billing.model.JoiningFeeState;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentService;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
        when(billingDocumentService.findJoiningFeeState(tenant)).thenReturn(joiningFeeState());
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

    /** The contract with eegfaktura-web: participantId, energyBilled, joiningFee {documentNumber, documentDate} | null. */
    @Test
    void joiningFeeStateIsListedForTheOwnTenant() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(GET, "/api/billingDocuments/tenant/{tenantId}/joiningFeeState")
                        .request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].participantId").value("00000000-0000-4000-8000-000000000001"))
                .andExpect(jsonPath("$[0].energyBilled").value(true))
                .andExpect(jsonPath("$[0].joiningFee.documentNumber").value("RG202600012"))
                .andExpect(jsonPath("$[0].joiningFee.documentDate").value("2026-01-15"))
                .andExpect(jsonPath("$[1].energyBilled").value(false))
                // written as null, not left out
                .andExpect(content().string(containsString("\"energyBilled\":false,\"joiningFee\":null}")));
        verify(billingDocumentService).findJoiningFeeState(OWN);
    }

    private static List<JoiningFeeState> joiningFeeState() {
        JoiningFeeState.Document document = new JoiningFeeState.Document();
        document.setDocumentNumber("RG202600012");
        document.setDocumentDate(LocalDate.of(2026, 1, 15));
        JoiningFeeState billed = new JoiningFeeState();
        billed.setParticipantId("00000000-0000-4000-8000-000000000001");
        billed.setEnergyBilled(true);
        billed.setJoiningFee(document);
        JoiningFeeState open = new JoiningFeeState();
        open.setParticipantId("00000000-0000-4000-8000-000000000002");
        return List.of(billed, open);
    }

    @Test
    void deleteRemovesTheDocument() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(DELETE, "/api/billingDocuments/{id}").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isNoContent());
        verify(billingDocumentService).delete(ID);
    }
}
