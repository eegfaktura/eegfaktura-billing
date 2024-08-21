package org.vfeeg.eegfaktura.billing.service;

import java.util.List;
import java.util.UUID;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.vfeeg.eegfaktura.billing.domain.BillingDocument;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentItem;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentItemDTO;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentItemRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentRepository;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;


@Service
@Transactional
public class BillingDocumentItemService {

    private final BillingDocumentItemRepository billingDocumentItemRepository;
    private final BillingDocumentRepository billingDocumentRepository;

    public BillingDocumentItemService(
            final BillingDocumentItemRepository billingDocumentItemRepository,
            final BillingDocumentRepository billingDocumentRepository) {
        this.billingDocumentItemRepository = billingDocumentItemRepository;
        this.billingDocumentRepository = billingDocumentRepository;
    }

    public List<BillingDocumentItemDTO> findAll() {
        final List<BillingDocumentItem> billingDocumentItems = billingDocumentItemRepository.findAll(Sort.by("id"));
        return billingDocumentItems.stream()
                .map((billingDocumentItem) -> mapToDTO(billingDocumentItem, new BillingDocumentItemDTO()))
                .toList();
    }

    public BillingDocumentItemDTO get(final UUID id) {
        return billingDocumentItemRepository.findById(id)
                .map(billingDocumentItem -> mapToDTO(billingDocumentItem, new BillingDocumentItemDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public UUID create(final BillingDocumentItemDTO billingDocumentItemDTO) {
        final BillingDocumentItem billingDocumentItem = new BillingDocumentItem();
        mapToEntity(billingDocumentItemDTO, billingDocumentItem);
        return billingDocumentItemRepository.save(billingDocumentItem).getId();
    }

    public void update(final UUID id, final BillingDocumentItemDTO billingDocumentItemDTO) {
        final BillingDocumentItem billingDocumentItem = billingDocumentItemRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(billingDocumentItemDTO, billingDocumentItem);
        billingDocumentItemRepository.save(billingDocumentItem);
    }

    public void delete(final UUID id) {
        billingDocumentItemRepository.deleteById(id);
    }

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

    private BillingDocumentItem mapToEntity(final BillingDocumentItemDTO billingDocumentItemDTO,
            final BillingDocumentItem billingDocumentItem) {
        billingDocumentItem.setTenantId(billingDocumentItemDTO.getTenantId());
        billingDocumentItem.setClearingPeriodType(billingDocumentItemDTO.getClearingPeriodType());
        billingDocumentItem.setClearingPeriodIdentifier(billingDocumentItemDTO.getClearingPeriodIdentifier());
        billingDocumentItem.setAmount(billingDocumentItemDTO.getAmount());
        billingDocumentItem.setMeteringPointId(billingDocumentItemDTO.getMeteringPointId());
        billingDocumentItem.setMeteringPointType(billingDocumentItemDTO.getMeteringPointType());
        billingDocumentItem.setText(billingDocumentItemDTO.getText());
        billingDocumentItem.setDocumentText(billingDocumentItemDTO.getDocumentText());
        billingDocumentItem.setTariffName(billingDocumentItemDTO.getTariffName());
        billingDocumentItem.setUnit(billingDocumentItemDTO.getUnit());
        billingDocumentItem.setPricePerUnit(billingDocumentItemDTO.getPricePerUnit());
        billingDocumentItem.setPpuUnit(billingDocumentItemDTO.getPpuUnit());
        billingDocumentItem.setNetValue(billingDocumentItemDTO.getNetValue());
        billingDocumentItem.setDiscountPercent(billingDocumentItemDTO.getDiscountPercent());
        billingDocumentItem.setVatPercent(billingDocumentItemDTO.getVatPercent());
        billingDocumentItem.setVatValueInEuro(billingDocumentItemDTO.getVatValueInEuro());
        billingDocumentItem.setGrossValue(billingDocumentItemDTO.getGrossValue());
        final BillingDocument billingDocument = billingDocumentItemDTO.getBillingDocumentId() == null ? null : billingDocumentRepository.findById(billingDocumentItemDTO.getBillingDocumentId())
                .orElseThrow(() -> new NotFoundException("billingDocument not found"));
        billingDocumentItem.setBillingDocument(billingDocument);
        return billingDocumentItem;
    }

}
