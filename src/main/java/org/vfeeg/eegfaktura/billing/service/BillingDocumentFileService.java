package org.vfeeg.eegfaktura.billing.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentFile;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentFileDTO;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentFileRepository;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;


@Service
public class BillingDocumentFileService {

    private final BillingDocumentFileRepository billingDocumentFileRepository;

    public BillingDocumentFileService(final BillingDocumentFileRepository fileRepository) {
        this.billingDocumentFileRepository = fileRepository;
    }

    public List<BillingDocumentFileDTO> findByBillingDocumentId(UUID billingDocumentId) {
        final List<BillingDocumentFile> billingDocumentFiles = billingDocumentFileRepository
                .findByBillingDocumentId(billingDocumentId);
        return billingDocumentFiles.stream()
                .map((billingDocumentFile) -> mapToDTO(billingDocumentFile, new BillingDocumentFileDTO()))
                .toList();
    }

    public List<BillingDocumentFileDTO> findByTenantId(String tenantId) {
        final List<BillingDocumentFile> billingDocumentFiles = billingDocumentFileRepository.findByTenantId(tenantId);
        return billingDocumentFiles.stream()
                .map((billingDocumentFile) -> mapToDTO(billingDocumentFile, new BillingDocumentFileDTO()))
                .toList();
    }

    public BillingDocumentFileDTO get(final UUID id) {
        return billingDocumentFileRepository.findById(id)
                .map(billingDocumentFile -> mapToDTO(billingDocumentFile,
                        new BillingDocumentFileDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public void delete(final UUID id) {
        billingDocumentFileRepository.deleteById(id);
    }

    private BillingDocumentFileDTO mapToDTO(final BillingDocumentFile billingDocumentFile
            , final BillingDocumentFileDTO billingDocumentFileDTO) {
        billingDocumentFileDTO.setId(billingDocumentFile.getId());
        billingDocumentFileDTO.setName(billingDocumentFile.getName());
        billingDocumentFileDTO.setMimeType(billingDocumentFile.getMimeType());
        billingDocumentFileDTO.setTenantId(billingDocumentFile.getTenantId());
        billingDocumentFileDTO.setFileDataId(billingDocumentFile.getFileDataId());
        billingDocumentFileDTO.setParticipantId(billingDocumentFile.getBillingDocument() == null
                ? null : billingDocumentFile.getBillingDocument().getParticipantId());
        billingDocumentFileDTO.setBillingDocumentId(billingDocumentFile.getBillingDocument() == null
                ? null : billingDocumentFile.getBillingDocument().getId());
        return billingDocumentFileDTO;
    }

}
