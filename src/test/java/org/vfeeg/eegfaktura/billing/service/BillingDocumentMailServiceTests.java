package org.vfeeg.eegfaktura.billing.service;

import jakarta.mail.MessagingException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vfeeg.eegfaktura.billing.domain.BillingDocument;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentFile;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentType;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.domain.FileData;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentFileRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;
import org.vfeeg.eegfaktura.billing.util.AppProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Status and protocol of {@code sendAllBillingDocuments}; template and logo are the real classpath files. */
@ExtendWith(MockitoExtension.class)
class BillingDocumentMailServiceTests {

    private static final UUID RUN_ID = UUID.randomUUID();

    @Mock private EmailService emailService;
    @Mock private BillingDocumentFileRepository billingDocumentFileRepository;
    @Mock private FileDataRepository fileDataRepository;
    @Mock private BillingRunRepository billingRunRepository;
    @Mock private BillingDocumentRepository billingDocumentRepository;

    private BillingDocumentMailService service;
    private BillingRun run;
    private final List<String> savedStatuses = new ArrayList<>();
    private final Map<UUID, UUID> fileDataIdByDocument = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new BillingDocumentMailService(emailService, billingDocumentFileRepository, fileDataRepository,
                new AppProperties(), billingRunRepository, billingDocumentRepository);
        run = BillingRun.builder().id(RUN_ID).tenantId("TE100100").clearingPeriodIdentifier("Abr_YQ-2024-3")
                .build();
        lenient().when(billingRunRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
        lenient().when(billingRunRepository.saveAndFlush(any(BillingRun.class))).thenAnswer(invocation -> {
            savedStatuses.add("flush:" + run.getMailStatus());
            return run;
        });
        lenient().when(billingRunRepository.save(any(BillingRun.class))).thenAnswer(invocation -> {
            savedStatuses.add("save:" + run.getMailStatus());
            return run;
        });
    }

    @Test
    void statusGoesInProgressThenSentAndProtocolListsTheRecipient() throws Exception {
        BillingDocument anna = document("anna@example.at", "Anna Muster", "001");
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of(anna));
        when(emailService.sendEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(List.of());

        String protocol = service.sendAllBillingDocuments(RUN_ID);

        assertThat(savedStatuses, contains("flush:IN PROGRESS", "save:SENT"));
        assertThat(run.getMailStatus(), is("SENT"));
        assertThat(run.getMailStatusDateTime(), is(notNullValue()));
        assertThat(run.getSendMailProtocol(), is(protocol));
        assertThat(protocol, startsWith("Send mail start at "));
        assertThat(protocol, containsString("anna@example.at OK,"));
        assertThat(protocol, containsString("Send mail finished at "));
    }

    @Test
    void mailIsBuiltFromTheDocument() throws Exception {
        BillingDocument anna = document("anna@example.at", "Anna Muster", "001");
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of(anna));

        service.sendAllBillingDocuments(RUN_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, byte[]>> attachments = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(eq("no-reply@eegfaktura.at"), eq("anna@example.at"), eq("office@eeg.at"),
                eq("Rechnung Abr_YQ-2024-3"), body.capture(), attachments.capture());
        assertThat(attachments.getValue(), hasKey("R202400001.pdf"));
        assertThat(attachments.getValue(), hasKey(BillingDocumentMailService.ATTACHMENT_LOGO_NAME));
        assertThat(body.getValue(), containsString("Anna Muster"));
        assertThat(body.getValue(), containsString("01.07.2024 - 30.09.2024"));
        assertThat(body.getValue(), containsString("cid:" + BillingDocumentMailService.ATTACHMENT_LOGO_NAME));
    }

    @Test
    void rejectedAddressPartsAreAWarningNotAFailure() throws Exception {
        BillingDocument anna = document("anna@example.at;kaputt", "Anna Muster", "001");
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of(anna));
        when(emailService.sendEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(List.of("kaputt"));

        String protocol = service.sendAllBillingDocuments(RUN_ID);

        assertThat(protocol, containsString("anna@example.at;kaputt OK (nicht zugestellt an ungültige Adresse: kaputt),"));
        assertThat(protocol, not(containsString("FEHLER")));
    }

    @Test
    void oneFailingSendIsReportedWithParticipantAndTheOthersGoOut() throws Exception {
        BillingDocument anna = document("anna@example.at", "Anna Muster", "001");
        BillingDocument bert = document("bert@example.at", "Bert Beispiel", "002");
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of(anna, bert));
        when(emailService.sendEmail(anyString(), eq("anna@example.at"), anyString(), anyString(), anyString(),
                anyMap())).thenThrow(new MessagingException("smtp down"));
        when(emailService.sendEmail(anyString(), eq("bert@example.at"), anyString(), anyString(), anyString(),
                anyMap())).thenReturn(List.of());

        String protocol = service.sendAllBillingDocuments(RUN_ID);

        assertThat(protocol, containsString("anna@example.at (MitgliedsNr 001, Anna Muster) FEHLER,"));
        assertThat(protocol, containsString("bert@example.at OK,"));
        assertThat(run.getMailStatus(), is("SENT"));
    }

    @Test
    void documentWithoutFileIsAFailureWithPlaceholders() throws Exception {
        BillingDocument nobody = BillingDocument.builder().id(UUID.randomUUID()).recipientEmail(" ")
                .billingDocumentType(BillingDocumentType.INVOICE).build();
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of(nobody));
        when(billingDocumentFileRepository.findByBillingDocumentId(nobody.getId())).thenReturn(List.of());

        String protocol = service.sendAllBillingDocuments(RUN_ID);

        assertThat(protocol, containsString("  (MitgliedsNr -, -) FEHLER,"));
        verify(emailService, never()).sendEmail(any(), any(), any(), any(), any(), any());
    }

    @Test
    void missingFileDataIsAFailure() throws Exception {
        BillingDocument anna = document("anna@example.at", "Anna Muster", "001");
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of(anna));
        when(fileDataRepository.findById(fileDataIdByDocument.get(anna.getId()))).thenReturn(Optional.empty());

        assertThat(service.sendAllBillingDocuments(RUN_ID), containsString("(MitgliedsNr 001, Anna Muster) FEHLER,"));
        verify(emailService, never()).sendEmail(any(), any(), any(), any(), any(), any());
    }

    @Test
    void runWithAMailStatusIsNotSentAgain() {
        run.setMailStatus("SENT");

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.sendAllBillingDocuments(RUN_ID));

        assertThat(error.getMessage(), containsString("Status = SENT"));
        verify(billingRunRepository, never()).saveAndFlush(any());
        verifyNoInteractions(emailService, billingDocumentRepository);
    }

    @Test
    void emptyMailStatusCountsAsNotSent() {
        run.setMailStatus("");
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of());

        assertThat(service.sendAllBillingDocuments(RUN_ID), containsString("Send mail finished at "));
        assertThat(savedStatuses, contains("flush:IN PROGRESS", "save:SENT"));
    }

    @Test
    void unknownRunIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(billingRunRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.sendAllBillingDocuments(unknown));
        verifyNoInteractions(emailService);
    }

    /**
     * F7: a sender that throws for every document still ends with "SENT", which blocks a re-send although
     * no mail went out. The correct status is anything but "SENT" (name left to the fix).
     */
    @Test
    @Disabled("known-errors #18")
    void runWhereEveryMailFailedIsNotMarkedSent() throws Exception {
        BillingDocument anna = document("anna@example.at", "Anna Muster", "001");
        BillingDocument bert = document("bert@example.at", "Bert Beispiel", "002");
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of(anna, bert));
        when(emailService.sendEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenThrow(new MessagingException("smtp down"));

        service.sendAllBillingDocuments(RUN_ID);

        assertThat(run.getMailStatus(), is(not("SENT")));
    }

    private BillingDocument document(String email, String name, String participantNumber) {
        BillingDocument document = BillingDocument.builder().id(UUID.randomUUID()).recipientEmail(email)
                .recipientName(name).recipientParticipantNumber(participantNumber).issuerMail("office@eeg.at")
                .issuerName("EEG Sonnenstrom").footerText("IBAN folgt")
                .billingDocumentType(BillingDocumentType.INVOICE).clearingPeriodIdentifier("Abr_YQ-2024-3")
                .build();
        UUID fileDataId = UUID.randomUUID();
        fileDataIdByDocument.put(document.getId(), fileDataId);
        BillingDocumentFile file = new BillingDocumentFile();
        file.setFileDataId(fileDataId);
        FileData data = new FileData();
        data.setName("R202400001.pdf");
        data.setData(new byte[]{1, 2, 3});
        lenient().when(billingDocumentFileRepository.findByBillingDocumentId(document.getId()))
                .thenReturn(List.of(file));
        lenient().when(fileDataRepository.findById(fileDataId)).thenReturn(Optional.of(data));
        return document;
    }
}
