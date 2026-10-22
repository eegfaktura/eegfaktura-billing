package org.vfeeg.eegfaktura.billing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.vfeeg.eegfaktura.billing.domain.BillingDocument;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentFile;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentType;
import org.vfeeg.eegfaktura.billing.domain.BillingMailTemplate;
import org.vfeeg.eegfaktura.billing.domain.BillingMailTemplateFile;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.domain.FileData;
import org.vfeeg.eegfaktura.billing.repos.BillingConfigRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentFileRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingMailTemplateFileRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingMailTemplateRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingMasterdataRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;
import org.vfeeg.eegfaktura.billing.util.AppProperties;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sending a billing run with the EEG's mail text: default without a stored text (close to the
 * former FreeMarker mail), stored text with escaped values, and the fallback to the default.
 */
class BillingDocumentMailServiceTest {

    private static final String TENANT = "RC100200";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 7};

    private final EmailService emailService = mock(EmailService.class);
    private final BillingDocumentFileRepository documentFileRepository = mock(BillingDocumentFileRepository.class);
    private final FileDataRepository fileDataRepository = mock(FileDataRepository.class);
    private final BillingRunRepository runRepository = mock(BillingRunRepository.class);
    private final BillingDocumentRepository documentRepository = mock(BillingDocumentRepository.class);
    private final BillingMailTemplateRepository templateRepository = mock(BillingMailTemplateRepository.class);
    private final BillingMailTemplateFileRepository templateFileRepository = mock(BillingMailTemplateFileRepository.class);

    private BillingDocumentMailService service;
    private MailHtmlPolicy policy;
    private BillingRun run;
    private BillingDocument document;

    @BeforeEach
    void setUp() throws Exception {
        MailHtmlPolicy realPolicy = new MailHtmlPolicy();
        MailTemplateCatalog catalog = new MailTemplateCatalog(realPolicy);
        policy = spy(realPolicy);
        MailTemplateService mailTemplateService = new MailTemplateService(catalog, policy,
                new MailTemplateRenderer(policy), templateRepository, templateFileRepository, fileDataRepository,
                mock(BillingMasterdataRepository.class), mock(BillingConfigRepository.class), emailService,
                new AppProperties(), mock(jakarta.persistence.EntityManager.class));
        service = new BillingDocumentMailService(emailService, documentFileRepository, fileDataRepository,
                new AppProperties(), runRepository, documentRepository, mailTemplateService);

        run = BillingRun.builder().id(UUID.randomUUID()).tenantId(TENANT)
                .clearingPeriodIdentifier("Abr_YQ-2026-3").build();
        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));

        document = new BillingDocument();
        document.setId(UUID.randomUUID());
        document.setRecipientName("Erika Muster");
        document.setRecipientEmail("erika@example.com");
        document.setBillingDocumentType(BillingDocumentType.INVOICE);
        document.setClearingPeriodIdentifier("Abr_YQ-2026-3");
        document.setIssuerName("EEG Sonnenschein");
        document.setIssuerMail("office@sonnenschein.example");
        document.setFooterText("Sitz: Musterdorf\nZVR 123");
        when(documentRepository.findByBillingRunId(run.getId())).thenReturn(List.of(document));

        BillingDocumentFile documentFile = new BillingDocumentFile();
        documentFile.setFileDataId(UUID.randomUUID());
        when(documentFileRepository.findByBillingDocumentId(document.getId())).thenReturn(List.of(documentFile));
        FileData pdf = new FileData();
        pdf.setName("Rechnung-1.pdf");
        pdf.setData("%PDF-1.7".getBytes());
        when(fileDataRepository.findById(documentFile.getFileDataId())).thenReturn(Optional.of(pdf));

        when(templateRepository.findByTenantIdAndMailKey(anyString(), anyString())).thenReturn(Optional.empty());
        when(emailService.sendEmail(anyString(), anyString(), any(), anyString(), anyString(), any(List.class), any(Map.class)))
                .thenReturn(List.of());
    }

    private record Sent(String subject, String html, List<EmailService.InlineImage> inline, Map<String, byte[]> attachments) {
    }

    @SuppressWarnings("unchecked")
    private Sent sent() throws Exception {
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<EmailService.InlineImage>> inline = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Map<String, byte[]>> attachments = ArgumentCaptor.forClass(Map.class);
        verify(emailService).sendEmail(eq("no-reply@eegfaktura.at"), eq("erika@example.com"),
                eq("office@sonnenschein.example"), subject.capture(), html.capture(), inline.capture(),
                attachments.capture());
        return new Sent(subject.getValue(), html.getValue(), inline.getValue(), attachments.getValue());
    }

    private void stored(String subject, String body) {
        BillingMailTemplate template = new BillingMailTemplate();
        template.setTenantId(TENANT);
        template.setMailKey(MailTemplateCatalog.BILLING_DOCUMENT);
        template.setSubject(subject);
        template.setBodyHtml(body);
        template.setVersion(1);
        template.setDefaultVersion(1);
        when(templateRepository.findByTenantIdAndMailKey(TENANT, MailTemplateCatalog.BILLING_DOCUMENT))
                .thenReturn(Optional.of(template));
    }

    @Test
    @DisplayName("Without a stored text the default goes out: today's subject pattern, text, logo and PDF")
    void defaultMail() throws Exception {
        String protocol = service.sendAllBillingDocuments(run.getId());

        Sent sent = sent();
        assertEquals("Rechnung 01.07.2026 - 30.09.2026 - EEG Sonnenschein", sent.subject());
        String text = EmailService.htmlToPlainText(sent.html());
        assertTrue(text.contains("Liebe/r Erika Muster,"), text);
        assertTrue(text.contains("anbei findest du deine Rechnung für den Abrechnungszeitraum 01.07.2026 - 30.09.2026."), text);
        assertTrue(text.contains("Mit lieben Grüßen,"), text);
        assertTrue(text.contains("EEG Sonnenschein"), text);
        assertTrue(text.contains("Sitz: Musterdorf\nZVR 123"), text);
        assertTrue(sent.html().contains("src=\"cid:eegfaktura-logo-1\""), sent.html());
        assertEquals("eegfaktura-logo-1", sent.inline().get(0).contentId());
        assertTrue(sent.attachments().containsKey("Rechnung-1.pdf"));
        assertFalse(protocol.contains("HINWEIS"), protocol);
    }

    @Test
    @DisplayName("Values are escaped: <script> in the member name is text, not HTML (also in the default)")
    void valuesAreEscaped() throws Exception {
        document.setRecipientName("<script>alert(1)</script> & Co");
        document.setIssuerName("EEG \"<b>\"");

        service.sendAllBillingDocuments(run.getId());

        Sent sent = sent();
        assertFalse(sent.html().contains("<script>"), sent.html());
        assertFalse(sent.html().contains("<b>"), sent.html());
        assertTrue(sent.html().contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; Co"), sent.html());
    }

    @Test
    @DisplayName("A stored text is used: subject, body, escaped values, own image inline, PDF stays attached")
    void storedText() throws Exception {
        UUID imageId = UUID.randomUUID();
        BillingMailTemplateFile image = new BillingMailTemplateFile();
        image.setId(imageId);
        image.setTenantId(TENANT);
        image.setMailKey(MailTemplateCatalog.BILLING_DOCUMENT);
        image.setKind(BillingMailTemplateFile.KIND_IMAGE);
        image.setFileDataId(UUID.randomUUID());
        image.setCommitted(true);
        FileData imageData = new FileData();
        imageData.setMimeType("image/png");
        imageData.setData(PNG);
        when(fileDataRepository.findById(image.getFileDataId())).thenReturn(Optional.of(imageData));
        when(templateFileRepository.findByTenantIdAndMailKeyOrderByDateCreated(TENANT, MailTemplateCatalog.BILLING_DOCUMENT))
                .thenReturn(List.of(image));
        stored("Deine {document.type} von {eeg.name}",
                "<p><img src=\"mailfile:" + imageId + "\" alt=\"Logo\" /></p>"
                        + "<p>Servus <span data-ph=\"recipient.name\">Name</span>!</p>"
                        + "<div data-ph=\"eeg.contactBlock\">Kontakt</div>");
        document.setRecipientName("<script>x</script>");
        document.setIssuerAddressLine1("Hauptplatz 1");
        document.setIssuerAddressLine2("1234 Musterdorf");

        service.sendAllBillingDocuments(run.getId());

        Sent sent = sent();
        assertEquals("Deine Rechnung von EEG Sonnenschein", sent.subject());
        assertTrue(sent.html().contains("Servus &lt;script&gt;x&lt;/script&gt;!"), sent.html());
        assertTrue(sent.html().contains("Hauptplatz 1<br />1234 Musterdorf<br />E-Mail: office&#64;sonnenschein.example")
                || sent.html().contains("Hauptplatz 1<br />1234 Musterdorf<br />E-Mail: office@sonnenschein.example"), sent.html());
        assertTrue(sent.html().contains("src=\"cid:mailfile-" + imageId + "\""), sent.html());
        assertFalse(sent.html().contains("eegfaktura-logo"), "no eegfaktura logo unless the text uses it");
        assertEquals(1, sent.inline().size());
        assertEquals("image/png", sent.inline().get(0).mimeType());
        assertTrue(sent.attachments().containsKey("Rechnung-1.pdf"));
    }

    @Test
    @DisplayName("A draft (upload not yet part of a saved version) is never sent")
    void draftsAreNeverSent() throws Exception {
        BillingMailTemplateFile draft = new BillingMailTemplateFile();
        draft.setId(UUID.randomUUID());
        draft.setTenantId(TENANT);
        draft.setMailKey(MailTemplateCatalog.BILLING_DOCUMENT);
        draft.setKind(BillingMailTemplateFile.KIND_IMAGE);
        draft.setFileDataId(UUID.randomUUID());
        draft.setCommitted(false);
        when(templateFileRepository.findByTenantIdAndMailKeyOrderByDateCreated(TENANT, MailTemplateCatalog.BILLING_DOCUMENT))
                .thenReturn(List.of(draft));
        stored("x", "<p>Text<img src=\"mailfile:" + draft.getId() + "\" /></p>");

        service.sendAllBillingDocuments(run.getId());

        Sent sent = sent();
        assertFalse(sent.html().contains("mailfile"), sent.html());
        assertTrue(sent.inline().isEmpty());
        verify(fileDataRepository, org.mockito.Mockito.never()).findById(draft.getFileDataId());
    }

    @Test
    @DisplayName("The stored text is sanitized once per run, not per mail")
    void sanitizedOncePerRun() throws Exception {
        stored("Hallo", "<p>Hallo <span data-ph=\"recipient.name\">x</span></p>");
        BillingDocument second = new BillingDocument();
        second.setId(UUID.randomUUID());
        second.setRecipientEmail("erika@example.com");
        second.setIssuerMail("office@sonnenschein.example");
        second.setBillingDocumentType(BillingDocumentType.INVOICE);
        when(documentRepository.findByBillingRunId(run.getId())).thenReturn(List.of(document, second));
        List<BillingDocumentFile> pdf = documentFileRepository.findByBillingDocumentId(document.getId());
        when(documentFileRepository.findByBillingDocumentId(second.getId())).thenReturn(pdf);

        service.sendAllBillingDocuments(run.getId());

        verify(emailService, times(2)).sendEmail(anyString(), anyString(), any(), anyString(), anyString(),
                any(List.class), any(Map.class));
        verify(policy, times(1)).sanitize(any(), any());
    }

    @Test
    @DisplayName("A stored text that cannot be rendered: the default is sent and the run protocol says so")
    void brokenStoredTextFallsBack() throws Exception {
        stored("Betreff {member.gone}", "<p>Hallo <span data-ph=\"member.gone\">x</span></p>");

        String protocol = service.sendAllBillingDocuments(run.getId());

        Sent sent = sent();
        assertEquals("Rechnung 01.07.2026 - 30.09.2026 - EEG Sonnenschein", sent.subject());
        assertTrue(EmailService.htmlToPlainText(sent.html()).contains("Liebe/r Erika Muster,"));
        assertTrue(protocol.contains(BillingDocumentMailService.TEMPLATE_FALLBACK_NOTE), protocol);
        assertTrue(protocol.contains("erika@example.com OK"), protocol);
    }
}
