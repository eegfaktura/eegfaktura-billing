package org.vfeeg.eegfaktura.billing.rest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.vfeeg.eegfaktura.billing.domain.BillingConfig;
import org.vfeeg.eegfaktura.billing.domain.FileData;
import org.vfeeg.eegfaktura.billing.model.BillingConfigDTO;
import org.vfeeg.eegfaktura.billing.repos.BillingConfigRepository;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;
import org.vfeeg.eegfaktura.billing.security.Authority;
import org.vfeeg.eegfaktura.billing.security.TenantContext;
import org.vfeeg.eegfaktura.billing.service.BillingConfigService;
import org.vfeeg.eegfaktura.billing.service.FileDataService;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mandantentrennung der Abrechnungs-Konfiguration.
 *
 * Bisher pruefte PUT /api/billingConfigs/{id} nur den Mandanten im Body. Wer die ID einer
 * fremden Konfiguration kannte, konnte sie damit auf den eigenen Mandanten umhaengen. Und die
 * Datei-IDs fuer Logo und Fusszeile kamen ungeprueft aus dem Body - ueber GET .../logoImage
 * war damit jede Datei lesbar, deren ID man kannte, auch Rechnungs-PDFs anderer Gemeinschaften.
 */
class BillingConfigTenantTests {

    private static final String OWN = "RC100200";
    private static final String FOREIGN = "RC999999";

    private BillingConfigRepository configRepository;
    private FileDataRepository fileDataRepository;
    private BillingConfigResource resource;

    @BeforeEach
    void setUp() {
        configRepository = mock(BillingConfigRepository.class);
        fileDataRepository = mock(FileDataRepository.class);
        when(configRepository.save(any(BillingConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        resource = new BillingConfigResource(
                new BillingConfigService(configRepository, fileDataRepository),
                new FileDataService(fileDataRepository));
        TenantContext.setCurrentTenant(new Authority(OWN));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.setCurrentTenant(null);
    }

    private BillingConfig stored(String tenant) {
        BillingConfig config = new BillingConfig();
        config.setId(UUID.randomUUID());
        config.setTenantId(tenant);
        config.setHeaderImageFileDataId(UUID.randomUUID());
        config.setFooterImageFileDataId(UUID.randomUUID());
        config.setCustomTemplateFileDataId(UUID.randomUUID());
        config.setFooterText("alt");
        when(configRepository.findById(config.getId())).thenReturn(Optional.of(config));
        return config;
    }

    private BillingConfigDTO body(String tenant) {
        BillingConfigDTO dto = new BillingConfigDTO();
        dto.setTenantId(tenant);
        dto.setFooterText("neu");
        return dto;
    }

    @Test
    @DisplayName("PUT auf eine fremde Konfiguration wird abgewiesen, auch mit eigenem Mandanten im Body")
    void putOnForeignConfigIsRejected() {
        BillingConfig foreign = stored(FOREIGN);

        assertThrows(AccessDeniedException.class,
                () -> resource.updateBillingConfig(foreign.getId(), body(OWN)));

        verify(configRepository, never()).save(any());
        assertEquals(FOREIGN, foreign.getTenantId());
    }

    @Test
    @DisplayName("PUT aendert Texte, aber weder Mandant noch Datei-IDs")
    void putKeepsTenantAndFileIds() {
        BillingConfig own = stored(OWN);
        UUID header = own.getHeaderImageFileDataId();
        UUID footer = own.getFooterImageFileDataId();
        UUID template = own.getCustomTemplateFileDataId();

        BillingConfigDTO dto = body(OWN);
        dto.setHeaderImageFileDataId(UUID.randomUUID()); // z. B. die ID eines fremden Rechnungs-PDFs
        dto.setFooterImageFileDataId(UUID.randomUUID());
        dto.setCustomTemplateFileDataId(UUID.randomUUID());

        resource.updateBillingConfig(own.getId(), dto);

        ArgumentCaptor<BillingConfig> saved = ArgumentCaptor.forClass(BillingConfig.class);
        verify(configRepository).save(saved.capture());
        assertEquals("neu", saved.getValue().getFooterText());
        assertEquals(OWN, saved.getValue().getTenantId());
        assertEquals(header, saved.getValue().getHeaderImageFileDataId());
        assertEquals(footer, saved.getValue().getFooterImageFileDataId());
        assertEquals(template, saved.getValue().getCustomTemplateFileDataId());
    }

    @Test
    @DisplayName("Logo-Upload setzt die neue Datei-ID weiterhin")
    void uploadStillSetsTheImageId() {
        BillingConfig own = stored(OWN);
        UUID newFileId = UUID.randomUUID();
        when(fileDataRepository.save(any(FileData.class))).thenAnswer(inv -> {
            FileData fileData = inv.getArgument(0);
            fileData.setId(newFileId);
            return fileData;
        });

        resource.uploadLogoImage(own.getId(),
                new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1, 2, 3}));

        assertEquals(newFileId, own.getHeaderImageFileDataId());
    }

    @Test
    @DisplayName("Logo-Download ohne Logo ist 404, nicht 500")
    void logoDownloadWithoutLogoIsNotFound() {
        BillingConfig own = stored(OWN);
        own.setHeaderImageFileDataId(null);

        assertThrows(NotFoundException.class, () -> resource.getLogoImage(own.getId()));
    }

    @Test
    @DisplayName("Fusszeilen-Download braucht keinen Datei-Parameter mehr")
    void footerDownloadWorksAsPlainGet() {
        BillingConfig own = stored(OWN);
        FileData footer = new FileData();
        footer.setId(own.getFooterImageFileDataId());
        footer.setMimeType("image/png");
        footer.setData(new byte[]{7});
        when(fileDataRepository.findById(footer.getId())).thenReturn(Optional.of(footer));

        assertArrayEquals(new byte[]{7}, resource.getFooterImage(own.getId()).getBody());
    }
}
