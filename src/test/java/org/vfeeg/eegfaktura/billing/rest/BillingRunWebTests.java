package org.vfeeg.eegfaktura.billing.rest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentDTO;
import org.vfeeg.eegfaktura.billing.model.BillingRunDTO;
import org.vfeeg.eegfaktura.billing.model.ParticipantAmount;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentArchiveService;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentMailService;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentService;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentXlsxService;
import org.vfeeg.eegfaktura.billing.service.BillingRunService;
import org.vfeeg.eegfaktura.billing.service.ParticipantAmountService;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.vfeeg.eegfaktura.billing.rest.Endpoint.ID;
import static org.vfeeg.eegfaktura.billing.rest.TestTokens.OWN;

@WebMvcTest(controllers = BillingRunResource.class)
class BillingRunWebTests extends EndpointMatrix {

    @MockitoBean
    BillingRunService billingRunService;
    @MockitoBean
    BillingDocumentService billingDocumentService;
    @MockitoBean
    BillingDocumentXlsxService billingDocumentXlsxService;
    @MockitoBean
    BillingDocumentArchiveService billingDocumentArchiveService;
    @MockitoBean
    BillingDocumentMailService billingDocumentMailService;
    @MockitoBean
    ParticipantAmountService participantAmountService;

    @Override
    protected List<Endpoint> endpoints() {
        return EndpointTable.of(BillingRunResource.class);
    }

    @Override
    protected Object[] services() {
        return new Object[]{billingRunService, billingDocumentService, billingDocumentXlsxService,
                billingDocumentArchiveService, billingDocumentMailService, participantAmountService};
    }

    @Override
    protected void stubRecordsOf(String tenant) {
        try {
            when(billingRunService.get(ID)).thenReturn(run(tenant));
            when(billingRunService.findByTenantIdAndClearingPeriodTypeAndClearingPeriodIdentifier(
                    anyString(), anyString(), anyString())).thenReturn(List.of(run(tenant)));
            when(participantAmountService.getParticipantAmountsByBillingRunId(ID))
                    .thenReturn(List.of(new ParticipantAmount()));
            when(billingDocumentService.findByBillingRunId(ID)).thenReturn(List.of(document(tenant)));
            when(billingDocumentXlsxService.createXlsx(ID)).thenReturn(new byte[]{1, 2, 3});
            when(billingDocumentArchiveService.createArchive(ID)).thenReturn(new byte[]{4, 5});
            when(billingDocumentMailService.sendAllBillingDocuments(ID)).thenReturn("2 mails sent");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    protected void stubUnknownIds() {
        when(billingRunService.get(any())).thenThrow(new NotFoundException());
    }

    static BillingRunDTO run(String tenant) {
        BillingRunDTO run = new BillingRunDTO();
        run.setId(ID);
        run.setTenantId(tenant);
        run.setClearingPeriodType("YQ");
        run.setClearingPeriodIdentifier("YQ-2024-1");
        return run;
    }

    static BillingDocumentDTO document(String tenant) {
        BillingDocumentDTO document = new BillingDocumentDTO();
        document.setId(UUID.randomUUID());
        document.setTenantId(tenant);
        document.setDocumentNumber("R202400001");
        return document;
    }

    private Endpoint row(HttpMethod method, String pattern) {
        return endpoint(method, "/api/billingRuns" + pattern);
    }

    @Test
    void runsOfAPeriodAreListedForTheOwnTenant() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(row(HttpMethod.GET, "/{tenantId}/{clearingPeriodType}/{clearingPeriodIdentifier}")
                        .request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tenantId").value(OWN))
                .andExpect(jsonPath("$[0].clearingPeriodIdentifier").value("YQ-2024-1"));
        verify(billingRunService).findByTenantIdAndClearingPeriodTypeAndClearingPeriodIdentifier(
                OWN, "YQ", "2024-Q1");
    }

    @Test
    void xlsxExportIsAnAttachmentNamedAfterThePeriod() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(row(HttpMethod.GET, "/{id}/billingDocuments/xlsx").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/octet-stream"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"abrechnung_YQ-2024-1_export.xlsx\""))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void archiveExportIsAZipAttachment() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(row(HttpMethod.GET, "/{id}/billingDocuments/archive").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"abrechnung_YQ-2024-1_export.zip\""))
                .andExpect(content().bytes(new byte[]{4, 5}));
    }

    @Test
    void sendMailReturnsTheProtocol() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(row(HttpMethod.GET, "/{id}/billingDocuments/sendmail").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(content().string("2 mails sent"));
    }

    /** The resource turns any failure of the mail run into 500 with the message as plain body. */
    @Test
    void sendMailFailureIs500WithTheMessage() throws Exception {
        stubRecordsOf(OWN);
        when(billingDocumentMailService.sendAllBillingDocuments(ID))
                .thenThrow(new IllegalStateException("mail run already in progress"));
        mvc.perform(row(HttpMethod.GET, "/{id}/billingDocuments/sendmail").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("mail run already in progress"));
    }

    @Test
    void deleteRemovesTheRun() throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(row(HttpMethod.DELETE, "/{id}").request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isNoContent());
        verify(billingRunService).delete(ID);
    }
}
