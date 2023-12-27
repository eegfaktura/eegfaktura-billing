package org.vfeeg.eegfaktura.billing.service;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.vfeeg.eegfaktura.billing.domain.BillingDocument;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentFile;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentFileDTO;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentFileRepository;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;


@Service
public class BillingDocumentFileService {

    private final BillingDocumentFileRepository billingDocumentFileRepository;
    private final BillingDocumentRepository billingDocumentRepository;

    public BillingDocumentFileService(final BillingDocumentFileRepository fileRepository,
                                      final BillingDocumentRepository billingDocumentRepository) {
        this.billingDocumentFileRepository = fileRepository;
        this.billingDocumentRepository = billingDocumentRepository;
    }

    public List<BillingDocumentFileDTO> findAll() {
        final List<BillingDocumentFile> billingDocumentFiles = billingDocumentFileRepository.findAll(Sort.by("id"));
        return billingDocumentFiles.stream()
                .map((billingDocumentFile) -> mapToDTO(billingDocumentFile, new BillingDocumentFileDTO()))
                .toList();
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

    public UUID create(final BillingDocumentFileDTO billingDocumentfileDTO) {
        final BillingDocumentFile billingDocumentFile = new BillingDocumentFile();
        mapToEntity(billingDocumentfileDTO, billingDocumentFile);
        return billingDocumentFileRepository.save(billingDocumentFile).getId();
    }

    public void update(final UUID id, final BillingDocumentFileDTO billingDocumentfileDTO) {
        final BillingDocumentFile billingDocumentFile = billingDocumentFileRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(billingDocumentfileDTO, billingDocumentFile);
        billingDocumentFileRepository.save(billingDocumentFile);
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

    private BillingDocumentFile mapToEntity(final BillingDocumentFileDTO billingDocumentFileDTO
            , final BillingDocumentFile file) {
        file.setName(billingDocumentFileDTO.getName());
        file.setMimeType(billingDocumentFileDTO.getMimeType());
        file.setTenantId(billingDocumentFileDTO.getTenantId());
        file.setFileDataId(billingDocumentFileDTO.getFileDataId());
        final BillingDocument billingDocument = billingDocumentFileDTO.getBillingDocumentId() == null
                ? null : billingDocumentRepository.findById(billingDocumentFileDTO.getBillingDocumentId())
                .orElseThrow(() -> new NotFoundException("billingDocument not found"));
        file.setBillingDocument(billingDocument);
        return file;
    }

}
