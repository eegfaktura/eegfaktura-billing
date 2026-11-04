package org.vfeeg.eegfaktura.billing.service;

import java.util.UUID;

import jakarta.transaction.Transactional;
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

    public BillingDocumentNumberDTO get(final UUID id) {
        return billingDocumentNumberRepository.findById(id)
                .map(billingDocumentNumber -> mapToDTO(billingDocumentNumber, new BillingDocumentNumberDTO()))
                .orElseThrow(NotFoundException::new);
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

}
