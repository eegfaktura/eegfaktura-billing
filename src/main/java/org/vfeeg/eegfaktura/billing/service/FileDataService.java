package org.vfeeg.eegfaktura.billing.service;

import java.util.List;
import java.util.UUID;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.Sort;
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

    public UUID create(final FileDataDTO fileDataDTO) {
        final FileData fileData = new FileData();
        mapToEntity(fileDataDTO, fileData);
        return fileDataRepository.save(fileData).getId();
    }

    public void update(final UUID id, final FileDataDTO fileDataDTO) {
        final FileData fileData = fileDataRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(fileDataDTO, fileData);
        fileDataRepository.save(fileData);
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

    private FileData mapToEntity(final FileDataDTO fileDataDTO, final FileData fileData) {
        fileData.setName(fileDataDTO.getName());
        fileData.setMimeType(fileDataDTO.getMimeType());
        fileData.setTenantId(fileDataDTO.getTenantId());
        fileData.setData(fileDataDTO.getData());
        return fileData;
    }

}
