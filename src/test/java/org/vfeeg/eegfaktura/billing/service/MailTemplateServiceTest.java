package org.vfeeg.eegfaktura.billing.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vfeeg.eegfaktura.billing.domain.BillingMailTemplateFile;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private MailTemplateService service;

    @BeforeEach
    void setUp() {
        MailHtmlPolicy policy = new MailHtmlPolicy();
        service = new MailTemplateService(new MailTemplateCatalog(policy), policy, new MailTemplateRenderer(policy),
                templateRepository, fileRepository, fileDataRepository, mock(BillingMasterdataRepository.class),
                mock(BillingConfigRepository.class), mock(EmailService.class), new AppProperties());
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
}
