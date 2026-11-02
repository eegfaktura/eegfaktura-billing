package org.vfeeg.eegfaktura.billing.service;

import jakarta.mail.MessagingException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
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
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
    // subject and text come from the EEG's mail text or the default (MailTemplateService, own tests); here the
    // service hands back a mail built from the values it gets, so the test sees what BillingDocumentMailService passes
    @Mock private MailTemplateService mailTemplateService;
    private static final EmailService.InlineImage LOGO =
            new EmailService.InlineImage(MailTemplateService.LOGO_CONTENT_ID, new byte[]{1}, "image/png");

    private BillingDocumentMailService service;
    private BillingRun run;
    private final List<String> savedStatuses = new ArrayList<>();
    private final Map<UUID, UUID> fileDataIdByDocument = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new BillingDocumentMailService(emailService, billingDocumentFileRepository, fileDataRepository,
                new AppProperties(), billingRunRepository, billingDocumentRepository, mailTemplateService);
        lenient().when(mailTemplateService.prepareForSend(anyString()))
                .thenReturn(new MailTemplateService.Prepared(false, null, null, null, Map.of(), null));
        lenient().when(mailTemplateService.render(any(), anyMap(), eq(true))).thenAnswer(invocation -> {
            Map<String, String> v = invocation.getArgument(1);
            return new MailTemplateService.RenderedMail(
                    v.get("document.type") + " " + v.get("document.period") + " - " + v.get("eeg.name"),
                    "<p>" + v.get("recipient.name") + ", " + v.get("document.period") + "</p>"
                            + "<img src=\"cid:" + MailTemplateService.LOGO_CONTENT_ID + "\">",
                    List.of(LOGO));
        });
        run = BillingRun.builder().id(RUN_ID).tenantId("TE100100").clearingPeriodIdentifier("Abr_YQ-2024-3")
                .build();
        lenient().when(billingRunRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
        lenient().when(billingRunRepository.claimMailRun(RUN_ID, "IN PROGRESS")).thenAnswer(invocation -> {
            savedStatuses.add("claim:IN PROGRESS");
            return 1;
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
        when(emailService.sendEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyList(), anyMap()))
                .thenReturn(List.of());

        String protocol = service.sendAllBillingDocuments(RUN_ID);

        assertThat(savedStatuses, contains("claim:IN PROGRESS", "save:SENT"));
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

        // the values for the mail text come from the document (period as readable date range since the mail texts)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> values = ArgumentCaptor.forClass(Map.class);
        verify(mailTemplateService).render(any(), values.capture(), eq(true));
        assertThat(values.getValue(), hasEntry("recipient.name", "Anna Muster"));
        assertThat(values.getValue(), hasEntry("document.period", "01.07.2024 - 30.09.2024"));
        assertThat(values.getValue(), hasEntry("eeg.name", "EEG Sonnenstrom"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, byte[]>> attachments = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EmailService.InlineImage>> inline = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(eq("no-reply@eegfaktura.at"), eq("anna@example.at"), eq("office@eeg.at"),
                eq("Rechnung 01.07.2024 - 30.09.2024 - EEG Sonnenstrom"), body.capture(), inline.capture(),
                attachments.capture());
        // the PDF is an attachment, the logo an inline image (no longer a second attachment)
        assertThat(attachments.getValue(), hasKey("R202400001.pdf"));
        assertThat(attachments.getValue().size(), is(1));
        assertThat(inline.getValue(), contains(LOGO));
        assertThat(body.getValue(), containsString("Anna Muster"));
        assertThat(body.getValue(), containsString("cid:" + MailTemplateService.LOGO_CONTENT_ID));
    }

    @Test
    void rejectedAddressPartsAreAWarningNotAFailure() throws Exception {
        BillingDocument anna = document("anna@example.at;kaputt", "Anna Muster", "001");
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of(anna));
        when(emailService.sendEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyList(), anyMap()))
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
                anyList(), anyMap())).thenThrow(new MessagingException("smtp down"));
        when(emailService.sendEmail(anyString(), eq("bert@example.at"), anyString(), anyString(), anyString(),
                anyList(), anyMap())).thenReturn(List.of());

        String protocol = service.sendAllBillingDocuments(RUN_ID);

        assertThat(protocol, containsString("anna@example.at (MitgliedsNr 001, Anna Muster) FEHLER,"));
        assertThat(protocol, containsString("bert@example.at OK,"));
        assertThat(run.getMailStatus(), is("SENT"));
    }

    /** Lines of BillingDocumentMailService's logger while sending the run. */
    private List<ch.qos.logback.classic.spi.ILoggingEvent> sendAndCaptureLog() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(BillingDocumentMailService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.sendAllBillingDocuments(RUN_ID);
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list;
    }

    private static ch.qos.logback.classic.spi.ILoggingEvent failureLine(
            List<ch.qos.logback.classic.spi.ILoggingEvent> lines) {
        return lines.stream().filter(e -> e.getFormattedMessage().startsWith("Failed to send mail"))
                .findFirst().orElseThrow();
    }

    /**
     * The SMTP error names the recipient: the line keeps the error (masked) and its type, not the address and not the
     * stack trace; the exception built around it carries the document id only (never-log list).
     */
    @Test
    void failedSendIsLoggedWithoutTheAddress() throws Exception {
        BillingDocument anna = document("anna@example.at", "Anna Muster", "001");
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of(anna));
        when(emailService.sendEmail(anyString(), eq("anna@example.at"), anyString(), anyString(), anyString(),
                anyList(), anyMap()))
                .thenThrow(new MessagingException("550 5.1.1 <anna@example.at>: Recipient address rejected"));

        ch.qos.logback.classic.spi.ILoggingEvent line = failureLine(sendAndCaptureLog());
        assertThat(line.getFormattedMessage(), is("Failed to send mail (billingDocumentId=" + anna.getId()
                + "): 550 5.1.1 <<email>>: Recipient address rejected"));
        assertThat(line.getThrowableProxy(), is((Object) null));
        assertThat(line.getKeyValuePairs().get(0).value, is((Object) MessagingException.class.getName()));
    }

    /** Any other failure (here an NPE while building the mail) keeps its stack trace and its own type. */
    @Test
    void otherFailureKeepsTheRootCauseWithStackTrace() throws Exception {
        BillingDocument anna = document("anna@example.at", "Anna Muster", "001");
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of(anna));
        when(emailService.sendEmail(anyString(), eq("anna@example.at"), anyString(), anyString(), anyString(),
                anyList(), anyMap())).thenThrow(new NullPointerException("no file"));

        ch.qos.logback.classic.spi.ILoggingEvent line = failureLine(sendAndCaptureLog());
        assertThat(line.getFormattedMessage(), is("Failed to send mail (billingDocumentId=" + anna.getId() + ")"));
        assertThat(line.getThrowableProxy().getClassName(), is(NullPointerException.class.getName()));
        assertThat(ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(line.getThrowableProxy()),
                not(containsString("anna@example.at")));
    }

    @Test
    void documentWithoutFileIsAFailureWithPlaceholders() throws Exception {
        BillingDocument nobody = BillingDocument.builder().id(UUID.randomUUID()).recipientEmail(" ")
                .billingDocumentType(BillingDocumentType.INVOICE).build();
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of(nobody));
        when(billingDocumentFileRepository.findByBillingDocumentId(nobody.getId())).thenReturn(List.of());

        String protocol = service.sendAllBillingDocuments(RUN_ID);

        assertThat(protocol, containsString("  (MitgliedsNr -, -) FEHLER,"));
        verify(emailService, never()).sendEmail(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void missingFileDataIsAFailure() throws Exception {
        BillingDocument anna = document("anna@example.at", "Anna Muster", "001");
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of(anna));
        when(fileDataRepository.findById(fileDataIdByDocument.get(anna.getId()))).thenReturn(Optional.empty());

        assertThat(service.sendAllBillingDocuments(RUN_ID), containsString("(MitgliedsNr 001, Anna Muster) FEHLER,"));
        verify(emailService, never()).sendEmail(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void runWithAMailStatusIsNotSentAgain() {
        run.setMailStatus("SENT");

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.sendAllBillingDocuments(RUN_ID));

        assertThat(error.getMessage(), containsString("Status = SENT"));
        verify(billingRunRepository, never()).claimMailRun(any(), any());
        verifyNoInteractions(emailService, billingDocumentRepository);
    }

    /** FAILED means no mail went out: unlike SENT it can be sent again, through the same claim. */
    @Test
    void runWithFailedMailStatusIsSentAgain() {
        run.setMailStatus("FAILED");
        when(billingRunRepository.claimMailRun(RUN_ID, "IN PROGRESS")).thenReturn(1);
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of());

        service.sendAllBillingDocuments(RUN_ID);

        verify(billingRunRepository).claimMailRun(RUN_ID, "IN PROGRESS");
        assertThat(run.getMailStatus(), is("SENT"));
    }

    /** A second click on a FAILED run that is sending again loses the claim. */
    @Test
    void failedRunThatLostTheClaimSendsNothing() {
        run.setMailStatus("FAILED");
        when(billingRunRepository.claimMailRun(RUN_ID, "IN PROGRESS")).thenReturn(0);

        assertThrows(RuntimeException.class, () -> service.sendAllBillingDocuments(RUN_ID));

        verifyNoInteractions(emailService, billingDocumentRepository);
    }

    /** A call that loses the claim sends nothing and leaves the winner's status alone. */
    @Test
    void lostClaimIsRefusedAndSendsNothing() {
        when(billingRunRepository.claimMailRun(RUN_ID, "IN PROGRESS")).thenReturn(0);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.sendAllBillingDocuments(RUN_ID));

        assertThat(error.getMessage(), containsString("nicht moeglich"));
        verifyNoInteractions(emailService, billingDocumentRepository);
        verify(billingRunRepository, never()).save(any());
    }

    @Test
    void emptyMailStatusCountsAsNotSent() {
        run.setMailStatus("");
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of());

        assertThat(service.sendAllBillingDocuments(RUN_ID), containsString("Send mail finished at "));
        assertThat(savedStatuses, contains("claim:IN PROGRESS", "save:SENT"));
    }

    @Test
    void unknownRunIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(billingRunRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.sendAllBillingDocuments(unknown));
        verifyNoInteractions(emailService);
    }

    /**
     * F7: a sender that throws for every document must not end with "SENT": no mail went out. The protocol
     * stays the record of what failed.
     */
    @Test
    void runWhereEveryMailFailedIsNotMarkedSent() throws Exception {
        BillingDocument anna = document("anna@example.at", "Anna Muster", "001");
        BillingDocument bert = document("bert@example.at", "Bert Beispiel", "002");
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of(anna, bert));
        when(emailService.sendEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyList(), anyMap()))
                .thenThrow(new MessagingException("smtp down"));

        String protocol = service.sendAllBillingDocuments(RUN_ID);

        assertThat(run.getMailStatus(), is("FAILED"));
        assertThat(run.getMailStatusDateTime(), is(notNullValue()));
        assertThat(run.getSendMailProtocol(), is(protocol));
        assertThat(protocol, containsString("Send mail finished at "));
    }

    /** F7: an exception before the first mail ends the run as FAILED, not stuck in "IN PROGRESS". */
    @Test
    void exceptionBeforeTheFirstMailLeavesFailedNotInProgress() {
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenThrow(new IllegalStateException("db gone"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.sendAllBillingDocuments(RUN_ID));

        assertThat(error.getMessage(), is("db gone"));
        assertThat(savedStatuses, contains("claim:IN PROGRESS", "save:FAILED"));
        assertThat(run.getSendMailProtocol(), containsString(BillingDocumentMailService.ABORTED_NOTE));
        verifyNoInteractions(emailService);
    }

    /** F7: mails went out, then the status write fails once: the second write says SENT, the caller sees the error. */
    @Test
    void exceptionAfterMailsWentOutLeavesSent() throws Exception {
        BillingDocument anna = document("anna@example.at", "Anna Muster", "001");
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of(anna));
        when(billingRunRepository.save(any(BillingRun.class)))
                .thenThrow(new IllegalStateException("write failed"))
                .thenAnswer(invocation -> {
                    savedStatuses.add("save:" + run.getMailStatus());
                    return run;
                });

        assertThrows(IllegalStateException.class, () -> service.sendAllBillingDocuments(RUN_ID));

        assertThat(savedStatuses, contains("claim:IN PROGRESS", "save:SENT"));
        assertThat(run.getSendMailProtocol(), containsString("anna@example.at OK,"));
        assertThat(run.getSendMailProtocol(), containsString(BillingDocumentMailService.ABORTED_NOTE));
    }

    /** F7: nothing to send is no failed mail run, also when the first status write fails; one "finished" line. */
    @Test
    void statusWriteFailureOnAnEmptyRunStaysSentWithOneFinishedLine() {
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenReturn(List.of());
        when(billingRunRepository.save(any(BillingRun.class)))
                .thenThrow(new IllegalStateException("write failed"))
                .thenAnswer(invocation -> {
                    savedStatuses.add("save:" + run.getMailStatus());
                    return run;
                });

        assertThrows(IllegalStateException.class, () -> service.sendAllBillingDocuments(RUN_ID));

        assertThat(savedStatuses, contains("claim:IN PROGRESS", "save:SENT"));
        assertThat(run.getSendMailProtocol().split("Send mail finished at ", -1).length - 1, is(1));
    }

    /** F7: an Error (out of memory in a large run) passes the per-document handling, the status is still final. */
    @Test
    void errorInTheMailRunLeavesAFinalStatus() {
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenThrow(new OutOfMemoryError("heap"));

        assertThrows(OutOfMemoryError.class, () -> service.sendAllBillingDocuments(RUN_ID));

        assertThat(savedStatuses, contains("claim:IN PROGRESS", "save:FAILED"));
    }

    /** F7: when the status cannot be written at all the original error reaches the caller. */
    @Test
    void statusWriteFailureKeepsTheOriginalError() {
        when(billingDocumentRepository.findByBillingRunId(RUN_ID)).thenThrow(new IllegalStateException("db gone"));
        when(billingRunRepository.save(any(BillingRun.class))).thenThrow(new IllegalStateException("write failed"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.sendAllBillingDocuments(RUN_ID));

        assertThat(error.getMessage(), is("db gone"));
        assertThat(error.getSuppressed()[0].getMessage(), is("write failed"));
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
