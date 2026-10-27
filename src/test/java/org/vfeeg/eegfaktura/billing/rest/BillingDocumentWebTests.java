package org.vfeeg.eegfaktura.billing.rest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.vfeeg.eegfaktura.billing.model.JoiningFeeState;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentService;
import org.vfeeg.eegfaktura.billing.service.JoiningFeeService;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
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

    @MockitoBean
    JoiningFeeService joiningFeeService;

    @Override
    protected List<Endpoint> endpoints() {
        return EndpointTable.of(BillingDocumentResource.class);
    }

    @Override
    protected Object[] services() {
        return new Object[]{billingDocumentService, joiningFeeService};
    }

    @Override
    protected void stubRecordsOf(String tenant) {
        when(billingDocumentService.get(ID)).thenReturn(BillingRunWebTests.document(tenant));
        when(billingDocumentService.findByTenantIdAndYear(tenant, 2024))
                .thenReturn(List.of(BillingRunWebTests.document(tenant)));
        when(joiningFeeService.findJoiningFeeState(tenant)).thenReturn(joiningFeeState());
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
                .andExpect(jsonPath("$[0].requested").value(false))
                .andExpect(jsonPath("$[1].requested").value(true))
                .andExpect(jsonPath("$[2].paidExternally.paidOn").value("2026-01-10"))
                .andExpect(jsonPath("$[2].paidExternally.note").value("bar beim Beitritt"))
                // written as null, not left out
                .andExpect(content().string(containsString(
                        "\"energyBilled\":false,\"joiningFee\":null,\"requested\":true,\"paidExternally\":null}")));
        verify(joiningFeeService).findJoiningFeeState(OWN);
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
        open.setRequested(true);
        JoiningFeeState.PaidExternally payment = new JoiningFeeState.PaidExternally();
        payment.setPaidOn(LocalDate.of(2026, 1, 10));
        payment.setNote("bar beim Beitritt");
        JoiningFeeState paid = new JoiningFeeState();
        paid.setParticipantId("00000000-0000-4000-8000-000000000003");
        paid.setPaidExternally(payment);
        return List.of(billed, open, paid);
    }

    /** PUT passes tenant, member id, the body and the token's user to the service and answers 204. */
    @Test
    void joiningFeeRecordIsSavedWithTheUser() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(PUT, EndpointTable.JOINING_FEE_RECORD).request(OWN, OWN, TestTokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"PAID_EXTERNALLY\",\"paidOn\":\"2026-01-10\",\"note\":\"bar\"}"))
                .andExpect(status().isNoContent());
        verify(joiningFeeService).saveRecord(eq(OWN), eq(ID.toString()),
                argThat(r -> "PAID_EXTERNALLY".equals(r.getKind()) && "2026-01-10".equals(r.getPaidOn())
                        && "bar".equals(r.getNote())), any());
    }

    /** The service's 409 (fee already billed) and 400 (invalid body) reach the client with their reason. */
    @Test
    void conflictAndBadRequestOfTheServiceReachTheClient() throws Exception {
        stubRecordsOf(OWN);
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "bereits verrechnet"))
                .when(joiningFeeService).deleteRecord(OWN, ID.toString());
        mvc.perform(endpoint(DELETE, EndpointTable.JOINING_FEE_RECORD).request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.httpStatus").value(409));
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind fehlt"))
                .when(joiningFeeService).saveRecord(eq(OWN), eq(ID.toString()), any(), any());
        mvc.perform(endpoint(PUT, EndpointTable.JOINING_FEE_RECORD).request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRemovesTheDocument() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(endpoint(DELETE, "/api/billingDocuments/{id}").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isNoContent());
        verify(billingDocumentService).delete(ID);
    }
}
