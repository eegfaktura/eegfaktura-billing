package org.vfeeg.eegfaktura.billing.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.model.BillingRunDTO;
import org.vfeeg.eegfaktura.billing.model.ParticipantAmount;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;


@Service
@Transactional

public class BillingRunService {

    private final BillingRunRepository billingRunRepository;

    public BillingRunService(final BillingRunRepository billingRunRepository) {
        this.billingRunRepository = billingRunRepository;
    }

    public List<BillingRunDTO> findAll() {
        final List<BillingRun> billingRuns = billingRunRepository.findAll(Sort.by("id"));
        return billingRuns.stream()
                .map((billingRun) -> mapToDTO(billingRun, new BillingRunDTO()))
                .toList();
    }

    public List<BillingRunDTO> findByTenantIdAndClearingPeriodTypeAndClearingPeriodIdentifier(
            String tenantId, String clearingPeriodType, String clearingPeriodIdentifier) {

        final List<BillingRun> billingRuns = billingRunRepository.
                findByTenantIdAndClearingPeriodTypeAndClearingPeriodIdentifier(
                        tenantId, clearingPeriodType, clearingPeriodIdentifier
                );
        return billingRuns.stream()
                .map((billingRun) -> mapToDTO(billingRun, new BillingRunDTO()))
                .toList();
    }

    public BillingRunDTO get(final UUID id) {
        return billingRunRepository.findById(id)
                .map(billingRun -> mapToDTO(billingRun, new BillingRunDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public UUID create(final BillingRunDTO billingRunDTO) {
        final BillingRun billingRun = new BillingRun();
        mapToEntity(billingRunDTO, billingRun);
        return billingRunRepository.save(billingRun).getId();
    }

    public void update(final UUID id, final BillingRunDTO billingRunDTO) {
        final BillingRun billingRun = billingRunRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(billingRunDTO, billingRun);
        billingRunRepository.save(billingRun);
    }

    public void delete(final UUID id) {
        billingRunRepository.deleteById(id);
    }

    private BillingRunDTO mapToDTO(final BillingRun billingRun, final BillingRunDTO billingRunDTO) {
        billingRunDTO.setId(billingRun.getId());
        billingRunDTO.setClearingPeriodType(billingRun.getClearingPeriodType());
        billingRunDTO.setClearingPeriodIdentifier(billingRun.getClearingPeriodIdentifier());
        billingRunDTO.setTenantId(billingRun.getTenantId());
        billingRunDTO.setRunStatus(billingRun.getRunStatus());
        billingRunDTO.setRunStatusDateTime(billingRun.getRunStatusDateTime());
        billingRunDTO.setMailStatus(billingRun.getMailStatus());
        billingRunDTO.setMailStatusDateTime(billingRun.getMailStatusDateTime());
        billingRunDTO.setSendMailProtocol(billingRun.getSendMailProtocol());
        billingRunDTO.setSepaStatus(billingRun.getSepaStatus());
        billingRunDTO.setSepaStatusDateTime(billingRun.getSepaStatusDateTime());
        billingRunDTO.setNumberOfInvoices(billingRun.getNumberOfInvoices());
        billingRunDTO.setNumberOfCreditNotes(billingRun.getNumberOfCreditNotes());
        return billingRunDTO;
    }

    private BillingRun mapToEntity(final BillingRunDTO billingRunDTO, final BillingRun billingRun) {
        billingRun.setClearingPeriodType(billingRunDTO.getClearingPeriodType());
        billingRun.setClearingPeriodIdentifier(billingRunDTO.getClearingPeriodIdentifier());
        billingRun.setTenantId(billingRunDTO.getTenantId());
        billingRun.setRunStatus(billingRunDTO.getRunStatus());
        billingRun.setRunStatusDateTime(billingRunDTO.getRunStatusDateTime());
        billingRun.setMailStatus(billingRunDTO.getMailStatus());
        billingRun.setMailStatusDateTime(billingRunDTO.getMailStatusDateTime());
        billingRun.setSendMailProtocol(billingRunDTO.getSendMailProtocol());
        billingRun.setSepaStatus(billingRunDTO.getSepaStatus());
        billingRun.setSepaStatusDateTime(billingRunDTO.getSepaStatusDateTime());
        billingRun.setNumberOfInvoices(billingRunDTO.getNumberOfInvoices());
        billingRun.setNumberOfCreditNotes(billingRunDTO.getNumberOfCreditNotes());
        return billingRun;
    }


}
