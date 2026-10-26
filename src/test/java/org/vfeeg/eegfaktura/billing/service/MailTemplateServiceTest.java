package org.vfeeg.eegfaktura.billing.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.vfeeg.eegfaktura.billing.domain.BillingMailTemplateFile;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.PreviewRequest;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.TestRequest;
import org.vfeeg.eegfaktura.billing.repos.BillingConfigRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingMailTemplateFileRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingMailTemplateRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingMasterdataRepository;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;
import org.vfeeg.eegfaktura.billing.security.Authority;
import org.vfeeg.eegfaktura.billing.security.TenantContext;
import org.vfeeg.eegfaktura.billing.util.AppProperties;

import java.time.OffsetDateTime;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests of MailTemplateService without a database. */
class MailTemplateServiceTest {

    private static final String TENANT = "RC100200";

    private final BillingMailTemplateRepository templateRepository = mock(BillingMailTemplateRepository.class);
    private final BillingMailTemplateFileRepository fileRepository = mock(BillingMailTemplateFileRepository.class);
    private final FileDataRepository fileDataRepository = mock(FileDataRepository.class);
    private final EmailService emailService = mock(EmailService.class);
    private MailTemplateService service;

    @BeforeEach
    void setUp() {
        MailHtmlPolicy policy = new MailHtmlPolicy();
        service = new MailTemplateService(new MailTemplateCatalog(policy), policy, new MailTemplateRenderer(policy),
                templateRepository, fileRepository, fileDataRepository, mock(BillingMasterdataRepository.class),
                mock(BillingConfigRepository.class), emailService, new AppProperties(),
                mock(jakarta.persistence.EntityManager.class));
        TenantContext.setCurrentTenant(new Authority(TENANT));
        when(templateRepository.findByTenantIdAndMailKey(anyString(), anyString())).thenReturn(Optional.empty());
    }

    @AfterEach
    void clear() {
        TenantContext.setCurrentTenant(null);
    }

    @Test
    void fileListDoesNotLoadTheBytes() {
        BillingMailTemplateFile file = new BillingMailTemplateFile();
        file.setId(UUID.randomUUID());
        file.setTenantId(TENANT);
        file.setMailKey(MailTemplateCatalog.BILLING_DOCUMENT);
        file.setKind(BillingMailTemplateFile.KIND_IMAGE);
        file.setFileDataId(UUID.randomUUID());
        file.setSize(9);
        file.setDateCreated(OffsetDateTime.now());
        when(fileRepository.findByTenantIdAndMailKeyOrderByDateCreated(TENANT, MailTemplateCatalog.BILLING_DOCUMENT))
                .thenReturn(List.of(file));
        FileDataRepository.FileMeta meta = mock(FileDataRepository.FileMeta.class);
        when(meta.getId()).thenReturn(file.getFileDataId());
        when(meta.getName()).thenReturn("logo.png");
        when(meta.getMimeType()).thenReturn("image/png");
        when(fileDataRepository.findMetaByIdIn(any())).thenReturn(List.of(meta));

        var dto = service.get(TENANT, MailTemplateCatalog.BILLING_DOCUMENT);

        assertEquals("logo.png", dto.files().get(0).filename());
        assertEquals("image/png", dto.files().get(0).mimeType());
        verify(fileDataRepository, never()).findById(any());
    }

    @Test
    void definitionHasTheFixedFooterAndTheDefaultWithoutLogo() {
        var dto = service.get(TENANT, MailTemplateCatalog.BILLING_DOCUMENT);

        assertEquals(MailTemplateCatalog.FIXED_FOOTER, dto.fixedFooter());
        assertEquals("versandt durch EEG Faktura, ein Service des VFEEG - https://vfeeg.org/", dto.fixedFooter());
        assertEquals(2, dto.defaultVersion());
        assertFalse(dto.defaultBodyHtml().contains("eegfaktura-logo"), dto.defaultBodyHtml());
        assertFalse(dto.defaultBodyHtml().contains("img"), dto.defaultBodyHtml());
        assertFalse(dto.defaultBodyHtml().contains("versandt"), dto.defaultBodyHtml());
        assertEquals(MailTemplateCatalog.FIXED_FOOTER, service.list(TENANT).get(0).fixedFooter());
    }

    @Test
    void previewEndsWithTheFixedFooter() {
        var preview = service.preview(TENANT, MailTemplateCatalog.BILLING_DOCUMENT,
                new PreviewRequest("x", "<p>Eigener Text</p>", null));

        assertTrue(preview.html().contains("<p>Eigener Text</p>" + MailTemplateCatalog.FIXED_FOOTER_HTML),
                preview.html());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testMailHasTheFixedFooterInHtmlAndText() throws Exception {
        when(emailService.sendEmail(anyString(), anyString(), any(), anyString(), anyString(), any(List.class),
                any(Map.class))).thenReturn(List.of());

        service.sendTest(TENANT, MailTemplateCatalog.BILLING_DOCUMENT,
                new TestRequest(MailTemplateService.RECIPIENT_ME, "x", "<p>Eigener Text</p>", null), "me@example.com");

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(anyString(), eq("me@example.com"), any(), anyString(), html.capture(),
                any(List.class), any(Map.class));
        assertTrue(html.getValue().contains("<p>Eigener Text</p>" + MailTemplateCatalog.FIXED_FOOTER_HTML),
                html.getValue());
        // the plain-text part is derived from this HTML at the send point (EmailService)
        String text = EmailService.htmlToPlainText(html.getValue());
        assertTrue(text.endsWith("Eigener Text\n" + MailTemplateCatalog.FIXED_FOOTER), text);
    }
}
