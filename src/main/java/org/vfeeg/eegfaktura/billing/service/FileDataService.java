package org.vfeeg.eegfaktura.billing.service;

import java.util.UUID;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.vfeeg.eegfaktura.billing.domain.FileData;
import org.vfeeg.eegfaktura.billing.model.FileDataDTO;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;


@Service
@Transactional

public class FileDataService {

    private final FileDataRepository fileDataRepository;

    public FileDataService(final FileDataRepository fileDataRepository) {
        this.fileDataRepository = fileDataRepository;
    }

    public FileDataDTO get(final UUID id) {
        return fileDataRepository.findById(id)
                .map(fileData -> mapToDTO(fileData, new FileDataDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public void delete(final UUID id) {
        fileDataRepository.deleteById(id);
    }

    private FileDataDTO mapToDTO(final FileData fileData, final FileDataDTO fileDataDTO) {
        fileDataDTO.setId(fileData.getId());
        fileDataDTO.setName(fileData.getName());
        fileDataDTO.setTenantId(fileData.getTenantId());
        fileDataDTO.setMimeType(fileData.getMimeType());
        fileDataDTO.setData(fileData.getData());
        return fileDataDTO;
    }

}
