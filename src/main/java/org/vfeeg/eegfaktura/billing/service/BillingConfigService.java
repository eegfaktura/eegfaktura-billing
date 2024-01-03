package org.vfeeg.eegfaktura.billing.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import org.vfeeg.eegfaktura.billing.domain.BillingConfig;
import org.vfeeg.eegfaktura.billing.domain.FileData;
import org.vfeeg.eegfaktura.billing.model.BillingConfigDTO;
import org.vfeeg.eegfaktura.billing.model.BillingConfigImageType;
import org.vfeeg.eegfaktura.billing.repos.BillingConfigRepository;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;

@Service
@Transactional
@Slf4j
public class BillingConfigService {

    public final static BillingConfig DEFAULT;
    static {
        // @TODO: Wenn i18n gewuenscht wird, dann an dieser Stelle sprach-resource-ids verwenden
        DEFAULT = new BillingConfig();

        DEFAULT.setBeforeItemsTextInvoice("Wir erlauben uns folgende Rechnung zu stellen:");
        DEFAULT.setBeforeItemsTextCreditNote("Wir erlauben uns folgende Gutschrift zu senden:");
        DEFAULT.setBeforeItemsTextInfo("Wir erlauben uns folgende Abrechnungsinformation zu senden:");

        DEFAULT.setAfterItemsTextInvoice("Wir bedanken uns für deine Teilnahme an unserer Energiegemeinschaft.");
        DEFAULT.setAfterItemsTextCreditNote("Wir bedanken uns für deine Teilnahme an unserer Energiegemeinschaft.");
        DEFAULT.setAfterItemsTextInfo("Wir bedanken uns für deine Teilnahme an unserer Energiegemeinschaft.");

        DEFAULT.setTermsTextInvoice("Dieser Betrag wird in 2 Wochen von deinem Konto per Lastschrift eingezogen.");
        DEFAULT.setTermsTextCreditNote("Dieser Betrag wird dir in den nächsten Tagen auf dein Konto überwiesen.");
        DEFAULT.setTermsTextInfo("Wir bitten, diesen Betrag uns in Rechnung zu stellen.");

        DEFAULT.setDocumentNumberSequenceLength(5);
    }

    private final FileDataRepository fileDataRepository;


    private final BillingConfigRepository billingConfigRepository;

    public BillingConfigService(final BillingConfigRepository billingConfigRepository
            , final FileDataRepository fileDataService) {

        this.billingConfigRepository = billingConfigRepository;
        this.fileDataRepository = fileDataService;

    }

    public void storeImage(BillingConfigDTO billingConfigDTO, BillingConfigImageType billingConfigImageType, MultipartFile multipartFile) {
        if (multipartFile.isEmpty()) throw new IllegalArgumentException("No File uploaded");
        try {
            FileData fileData = new FileData();
            fileData.setData(multipartFile.getInputStream().readAllBytes());
            fileData.setName(multipartFile.getOriginalFilename());
            String contentType = multipartFile.getContentType();
            if (contentType!=null
                    &&!contentType.equals("image/png")
                    &&!contentType.equals("image/jpg")
                    &&!contentType.equals("image/jpeg")
                    &&!contentType.equals("image/gif")) {
                throw new IllegalArgumentException("Filetype not supported (only png/jpg/gif)");
            }
            fileData.setMimeType(contentType);
            fileData = fileDataRepository.save(fileData);
            if (billingConfigImageType==BillingConfigImageType.LOGO_IMAGE) {
                if (billingConfigDTO.getHeaderImageFileDataId()!=null) {
                    fileDataRepository.deleteById(billingConfigDTO.getHeaderImageFileDataId());
                }
                billingConfigDTO.setHeaderImageFileDataId(fileData.getId());
            } else { //FOOTER_IMAGE
                if (billingConfigDTO.getFooterImageFileDataId()!=null) {
                    fileDataRepository.deleteById(billingConfigDTO.getFooterImageFileDataId());
                }
                billingConfigDTO.setFooterImageFileDataId(fileData.getId());
            }
            update(billingConfigDTO.getId(), billingConfigDTO);
        } catch (Exception e) {
            log.error("Unable to store uploaded file due to: "+e.getMessage(), e);
            throw new RuntimeException("Unable to store uploaded file due to: "+e, e);
        }
    }

    public void deleteImage(BillingConfigDTO billingConfigDTO, BillingConfigImageType billingConfigImageType) {

        UUID imageFileDataId = billingConfigImageType == BillingConfigImageType.LOGO_IMAGE ?
                billingConfigDTO.getHeaderImageFileDataId() :
                billingConfigDTO.getFooterImageFileDataId();

        if (imageFileDataId!=null) {
            fileDataRepository.deleteById(imageFileDataId);
            if (billingConfigImageType == BillingConfigImageType.LOGO_IMAGE) {
                billingConfigDTO.setHeaderImageFileDataId(null);
            } else {
                billingConfigDTO.setFooterImageFileDataId(null);
            }
            update(billingConfigDTO.getId(), billingConfigDTO);
        }
    }

    public List<BillingConfigDTO> findAll() {
        final List<BillingConfig> billingConfigs = billingConfigRepository.findAll(Sort.by("id"));
        return billingConfigs.stream()
                .map(billingConfig -> mapToDTO(billingConfig, new BillingConfigDTO()))
                .toList();
    }

    public BillingConfigDTO get(final UUID id) {
        return billingConfigRepository.findById(id)
                .map(billingConfig -> mapToDTO(billingConfig, new BillingConfigDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public BillingConfigDTO getByTenantId(final String tenantId) {
        return billingConfigRepository.findByTenantId(tenantId)
                .map(billingConfig -> mapToDTO(billingConfig, new BillingConfigDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public UUID create(final BillingConfigDTO billingConfigDTO) {
        final BillingConfig billingConfig = new BillingConfig();
        mapToEntity(billingConfigDTO, billingConfig);
        return billingConfigRepository.save(billingConfig).getId();
    }

    public void update(final UUID id, final BillingConfigDTO billingConfigDTO) {
        final BillingConfig billingConfig = billingConfigRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(billingConfigDTO, billingConfig);
        billingConfigRepository.save(billingConfig);
    }

    public void delete(final UUID id) {
        billingConfigRepository.deleteById(id);
    }

    private BillingConfigDTO mapToDTO(final BillingConfig billingConfig,
                                      final BillingConfigDTO billingConfigDTO) {
        billingConfigDTO.setId(billingConfig.getId());
        billingConfigDTO.setTenantId(billingConfig.getTenantId());
        billingConfigDTO.setHeaderImageFileDataId(billingConfig.getHeaderImageFileDataId());
        billingConfigDTO.setFooterImageFileDataId(billingConfig.getFooterImageFileDataId());
        billingConfigDTO.setCreateCreditNotesForAllProducers(billingConfig.isCreateCreditNotesForAllProducers());
        billingConfigDTO.setBeforeItemsTextInvoice(billingConfig.getBeforeItemsTextInvoice());
        billingConfigDTO.setBeforeItemsTextCreditNote(billingConfig.getBeforeItemsTextCreditNote());
        billingConfigDTO.setBeforeItemsTextInfo(billingConfig.getBeforeItemsTextInfo());
        billingConfigDTO.setAfterItemsTextInvoice(billingConfig.getAfterItemsTextInvoice());
        billingConfigDTO.setAfterItemsTextCreditNote(billingConfig.getAfterItemsTextCreditNote());
        billingConfigDTO.setAfterItemsTextInfo(billingConfig.getAfterItemsTextInfo());
        billingConfigDTO.setTermsTextInvoice(billingConfig.getTermsTextInvoice());
        billingConfigDTO.setTermsTextCreditNote(billingConfig.getTermsTextCreditNote());
        billingConfigDTO.setTermsTextInfo(billingConfig.getTermsTextInfo());
        billingConfigDTO.setFooterText(billingConfig.getFooterText());
        billingConfigDTO.setDocumentNumberSequenceLength(billingConfig.getDocumentNumberSequenceLength());
        billingConfigDTO.setCustomTemplateFileDataId(billingConfig.getCustomTemplateFileDataId());
        billingConfigDTO.setInvoiceNumberPrefix(billingConfig.getInvoiceNumberPrefix());
        billingConfigDTO.setCreditNoteNumberPrefix(billingConfig.getCreditNoteNumberPrefix());
        billingConfigDTO.setInvoiceNumberStart(billingConfig.getInvoiceNumberStart());
        billingConfigDTO.setCreditNoteNumberStart(billingConfig.getCreditNoteNumberStart());
        return billingConfigDTO;
    }

    private BillingConfig mapToEntity(final BillingConfigDTO billingConfigDTO,
                                      final BillingConfig billingConfig) {
        billingConfig.setTenantId(billingConfigDTO.getTenantId());
        billingConfig.setHeaderImageFileDataId(billingConfigDTO.getHeaderImageFileDataId());
        billingConfig.setFooterImageFileDataId(billingConfigDTO.getFooterImageFileDataId());
        billingConfig.setCreateCreditNotesForAllProducers(billingConfigDTO.isCreateCreditNotesForAllProducers());

        billingConfig.setBeforeItemsTextInvoice(billingConfigDTO.getBeforeItemsTextInvoice());
        billingConfig.setBeforeItemsTextCreditNote(billingConfigDTO.getBeforeItemsTextCreditNote());
        billingConfig.setBeforeItemsTextInfo(billingConfigDTO.getBeforeItemsTextInfo());

        billingConfig.setAfterItemsTextInvoice(billingConfigDTO.getAfterItemsTextInvoice());
        billingConfig.setAfterItemsTextCreditNote(billingConfigDTO.getAfterItemsTextCreditNote());
        billingConfig.setAfterItemsTextInfo(billingConfigDTO.getAfterItemsTextInfo());

        billingConfig.setTermsTextInvoice(billingConfigDTO.getTermsTextInvoice());
        billingConfig.setTermsTextCreditNote(billingConfigDTO.getTermsTextCreditNote());
        billingConfig.setTermsTextInfo(billingConfigDTO.getTermsTextInfo());

        billingConfig.setFooterText(billingConfigDTO.getFooterText());
        billingConfig.setDocumentNumberSequenceLength(billingConfigDTO.getDocumentNumberSequenceLength());
        billingConfig.setCustomTemplateFileDataId(billingConfigDTO.getCustomTemplateFileDataId());

        billingConfig.setInvoiceNumberPrefix(billingConfigDTO.getInvoiceNumberPrefix());
        billingConfig.setCreditNoteNumberPrefix(billingConfigDTO.getCreditNoteNumberPrefix());
        billingConfig.setInvoiceNumberStart(billingConfigDTO.getInvoiceNumberStart());
        billingConfig.setCreditNoteNumberStart(billingConfigDTO.getCreditNoteNumberStart());

        return billingConfig;
    }

}
