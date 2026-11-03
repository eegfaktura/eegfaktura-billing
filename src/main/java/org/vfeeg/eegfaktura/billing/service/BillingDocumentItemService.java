package org.vfeeg.eegfaktura.billing.service;

import java.util.UUID;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentItem;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentItemDTO;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentItemRepository;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;


@Service
@Transactional
public class BillingDocumentItemService {

    private final BillingDocumentItemRepository billingDocumentItemRepository;

    public BillingDocumentItemService(
            final BillingDocumentItemRepository billingDocumentItemRepository) {
        this.billingDocumentItemRepository = billingDocumentItemRepository;
    }

    public BillingDocumentItemDTO get(final UUID id) {
        return billingDocumentItemRepository.findById(id)
                .map(billingDocumentItem -> mapToDTO(billingDocumentItem, new BillingDocumentItemDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public void delete(final UUID id) {
        billingDocumentItemRepository.deleteById(id);
    }

    @SuppressWarnings("DuplicatedCode")
    private BillingDocumentItemDTO mapToDTO(final BillingDocumentItem billingDocumentItem,
            final BillingDocumentItemDTO billingDocumentItemDTO) {
        billingDocumentItemDTO.setId(billingDocumentItem.getId());
        billingDocumentItemDTO.setTenantId(billingDocumentItem.getTenantId());
        billingDocumentItemDTO.setClearingPeriodType(billingDocumentItem.getClearingPeriodType());
        billingDocumentItemDTO.setClearingPeriodIdentifier(billingDocumentItem.getClearingPeriodIdentifier());
        billingDocumentItemDTO.setAmount(billingDocumentItem.getAmount());
        billingDocumentItemDTO.setMeteringPointId(billingDocumentItem.getMeteringPointId());
        billingDocumentItemDTO.setMeteringPointType(billingDocumentItem.getMeteringPointType());
        billingDocumentItemDTO.setText(billingDocumentItem.getText());
        billingDocumentItemDTO.setDocumentText(billingDocumentItem.getDocumentText());
        billingDocumentItemDTO.setTariffName(billingDocumentItem.getTariffName());
        billingDocumentItemDTO.setTariffId(billingDocumentItem.getTariffId());
        billingDocumentItemDTO.setTariffVersion(billingDocumentItem.getTariffVersion());
        billingDocumentItemDTO.setUnit(billingDocumentItem.getUnit());
        billingDocumentItemDTO.setPricePerUnit(billingDocumentItem.getPricePerUnit());
        billingDocumentItemDTO.setPpuUnit(billingDocumentItem.getPpuUnit());
        billingDocumentItemDTO.setNetValue(billingDocumentItem.getNetValue());
        billingDocumentItemDTO.setDiscountPercent(billingDocumentItem.getDiscountPercent());
        billingDocumentItemDTO.setVatPercent(billingDocumentItem.getVatPercent());
        billingDocumentItemDTO.setVatValueInEuro(billingDocumentItem.getVatValueInEuro());
        billingDocumentItemDTO.setGrossValue(billingDocumentItem.getGrossValue());
        billingDocumentItemDTO.setBillingDocumentId(billingDocumentItem.getBillingDocument().getId());
        return billingDocumentItemDTO;
    }

}
