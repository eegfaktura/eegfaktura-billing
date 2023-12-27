package org.vfeeg.eegfaktura.billing.service;

import java.util.List;
import java.util.UUID;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentNumber;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentNumberDTO;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentNumberRepository;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;


@Service
@Transactional
public class BillingDocumentNumberService {

    private final BillingDocumentNumberRepository billingDocumentNumberRepository;

    public BillingDocumentNumberService(
            final BillingDocumentNumberRepository billingDocumentNumberRepository) {
        this.billingDocumentNumberRepository = billingDocumentNumberRepository;
    }

    public List<BillingDocumentNumberDTO> findAll() {
        final List<BillingDocumentNumber> billingDocumentNumbers = billingDocumentNumberRepository.findAll(Sort.by("id"));
        return billingDocumentNumbers.stream()
                .map((billingDocumentNumber) -> mapToDTO(billingDocumentNumber, new BillingDocumentNumberDTO()))
                .toList();
    }

    public BillingDocumentNumberDTO get(final UUID id) {
        return billingDocumentNumberRepository.findById(id)
                .map(billingDocumentNumber -> mapToDTO(billingDocumentNumber, new BillingDocumentNumberDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public UUID create(final BillingDocumentNumberDTO billingDocumentNumberDTO) {
        final BillingDocumentNumber billingDocumentNumber = new BillingDocumentNumber();
        mapToEntity(billingDocumentNumberDTO, billingDocumentNumber);
        return billingDocumentNumberRepository.save(billingDocumentNumber).getId();
    }

    public void update(final UUID id, final BillingDocumentNumberDTO billingDocumentNumberDTO) {
        final BillingDocumentNumber billingDocumentNumber = billingDocumentNumberRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(billingDocumentNumberDTO, billingDocumentNumber);
        billingDocumentNumberRepository.save(billingDocumentNumber);
    }

    public void delete(final UUID id) {
        billingDocumentNumberRepository.deleteById(id);
    }

    private BillingDocumentNumberDTO mapToDTO(final BillingDocumentNumber billingDocumentNumber,
            final BillingDocumentNumberDTO billingDocumentNumberDTO) {
        billingDocumentNumberDTO.setId(billingDocumentNumber.getId());
        billingDocumentNumberDTO.setTenantId(billingDocumentNumber.getTenantId());
        billingDocumentNumberDTO.setDocumentType(billingDocumentNumber.getDocumentType());
        billingDocumentNumberDTO.setYear(billingDocumentNumber.getYear());
        billingDocumentNumberDTO.setPrefix(billingDocumentNumber.getPrefix());
        billingDocumentNumberDTO.setSequenceNumber(billingDocumentNumber.getSequenceNumber());
        billingDocumentNumberDTO.setDocumentNumber(billingDocumentNumber.getDocumentNumber());
        return billingDocumentNumberDTO;
    }

    private BillingDocumentNumber mapToEntity(
            final BillingDocumentNumberDTO billingDocumentNumberDTO,
            final BillingDocumentNumber billingDocumentNumber) {
        billingDocumentNumber.setTenantId(billingDocumentNumberDTO.getTenantId());
        billingDocumentNumber.setDocumentType(billingDocumentNumberDTO.getDocumentType());
        billingDocumentNumber.setYear(billingDocumentNumberDTO.getYear());
        billingDocumentNumber.setPrefix(billingDocumentNumberDTO.getPrefix());
        billingDocumentNumber.setSequenceNumber(billingDocumentNumberDTO.getSequenceNumber());
        billingDocumentNumber.setDocumentNumber(billingDocumentNumberDTO.getDocumentNumber());
        return billingDocumentNumber;
    }

}
