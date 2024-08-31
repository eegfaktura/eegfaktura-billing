package org.vfeeg.eegfaktura.billing.service;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.vfeeg.eegfaktura.billing.domain.BillingDocument;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentDTO;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static java.time.temporal.TemporalAdjusters.firstDayOfYear;
import static java.time.temporal.TemporalAdjusters.lastDayOfYear;


@Service
@Transactional
public class BillingDocumentService {

    private final BillingDocumentRepository billingDocumentRepository;
    private final BillingRunRepository billingRunRepository;

    public BillingDocumentService(final BillingDocumentRepository billingDocumentRepository,
            final BillingRunRepository billingRunRepository) {
        this.billingDocumentRepository = billingDocumentRepository;
        this.billingRunRepository = billingRunRepository;
    }

    public List<BillingDocumentDTO> findAll() {
        final List<BillingDocument> billingDocuments = billingDocumentRepository.findAll(Sort.by("id"));
        return billingDocuments.stream()
                .map((billingDocument) -> mapToDTO(billingDocument, new BillingDocumentDTO()))
                .toList();
    }

    public List<BillingDocumentDTO> findByTenantIdAndYear(String tenantId, Integer year) {
        LocalDate instance = LocalDate.now().withYear(year);
        LocalDate dateStart = instance.with(firstDayOfYear());
        LocalDate dateEnd = instance.with(lastDayOfYear());

        final List<BillingDocument> billingDocuments =
                billingDocumentRepository.findByTenantIdAndDocumentDateBetween(tenantId, dateStart, dateEnd);
        return billingDocuments.stream()
                .map((billingDocument) -> mapToDTO(billingDocument, new BillingDocumentDTO()))
                .toList();
    }

    public List<BillingDocumentDTO> findByBillingRunId(UUID billingRunId) {
        final List<BillingDocument> billingDocuments = billingDocumentRepository.findByBillingRunId(billingRunId);
        return billingDocuments.stream()
                .map((billingDocument) -> mapToDTO(billingDocument, new BillingDocumentDTO()))
                .toList();
    }

    public BillingDocumentDTO get(final UUID id) {
        return billingDocumentRepository.findById(id)
                .map(billingDocument -> mapToDTO(billingDocument, new BillingDocumentDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public UUID create(final BillingDocumentDTO billingDocumentDTO) {
        final BillingDocument billingDocument = new BillingDocument();
        mapToEntity(billingDocumentDTO, billingDocument);
        return billingDocumentRepository.save(billingDocument).getId();
    }

    public void update(final UUID id, final BillingDocumentDTO billingDocumentDTO) {
        final BillingDocument billingDocument = billingDocumentRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(billingDocumentDTO, billingDocument);
        billingDocumentRepository.save(billingDocument);
    }

    public void delete(final UUID id) {
        billingDocumentRepository.deleteById(id);
    }

    private BillingDocumentDTO mapToDTO(final BillingDocument billingDocument,
            final BillingDocumentDTO billingDocumentDTO) {
        billingDocumentDTO.setId(billingDocument.getId());
        billingDocumentDTO.setDocumentNumber(billingDocument.getDocumentNumber());
        billingDocumentDTO.setDocumentDate(billingDocument.getDocumentDate());
        billingDocumentDTO.setStatus(billingDocument.getStatus());
        billingDocumentDTO.setClearingPeriodType(billingDocument.getClearingPeriodType());
        billingDocumentDTO.setBeforeItemsText(billingDocument.getBeforeItemsText());
        billingDocumentDTO.setAfterItemsText(billingDocument.getAfterItemsText());
        billingDocumentDTO.setTermsText(billingDocument.getTermsText());
        billingDocumentDTO.setFooterText(billingDocument.getFooterText());
        billingDocumentDTO.setTenantId(billingDocument.getTenantId());
        billingDocumentDTO.setParticipantId(billingDocument.getParticipantId());
        billingDocumentDTO.setRecipientName(billingDocument.getRecipientName());
        billingDocumentDTO.setRecipientFirstname(billingDocument.getRecipientFirstname());
        billingDocumentDTO.setRecipientLastname(billingDocument.getRecipientLastname());
        billingDocumentDTO.setRecipientParticipantNumber(billingDocument.getRecipientParticipantNumber());
        billingDocumentDTO.setRecipientAddressLine1(billingDocument.getRecipientAddressLine1());
        billingDocumentDTO.setRecipientAddressLine2(billingDocument.getRecipientAddressLine2());
        billingDocumentDTO.setRecipientAddressLine3(billingDocument.getRecipientAddressLine3());
        billingDocumentDTO.setRecipientBankIban(billingDocument.getRecipientBankIban());
        billingDocumentDTO.setRecipientBankName(billingDocument.getRecipientBankName());
        billingDocumentDTO.setRecipientBankOwner(billingDocument.getRecipientBankOwner());
        billingDocumentDTO.setRecipientSepaMandateReference(billingDocument.getRecipientSepaMandateReference());
        billingDocumentDTO.setRecipientSepaMandateIssueDate(billingDocument.getRecipientSepaMandateIssueDate());
        billingDocumentDTO.setRecipientEmail(billingDocument.getRecipientEmail());
        billingDocumentDTO.setRecipientTaxId(billingDocument.getRecipientTaxId());
        billingDocumentDTO.setRecipientVatId(billingDocument.getRecipientVatId());
        billingDocumentDTO.setIssuerName(billingDocument.getIssuerName());
        billingDocumentDTO.setIssuerAddressLine1(billingDocument.getIssuerAddressLine1());
        billingDocumentDTO.setIssuerAddressLine2(billingDocument.getIssuerAddressLine2());
        billingDocumentDTO.setIssuerAddressLine3(billingDocument.getIssuerAddressLine3());
        billingDocumentDTO.setIssuerPhone(billingDocument.getIssuerPhone());
        billingDocumentDTO.setIssuerMail(billingDocument.getIssuerMail());
        billingDocumentDTO.setIssuerWebsite(billingDocument.getIssuerWebsite());
        billingDocumentDTO.setIssuerTaxId(billingDocument.getIssuerTaxId());
        billingDocumentDTO.setIssuerVatId(billingDocument.getIssuerVatId());
        billingDocumentDTO.setIssuerCompanyRegisterNumber(billingDocument.getIssuerCompanyRegisterNumber());
        billingDocumentDTO.setIssuerBankName(billingDocument.getIssuerBankName());
        billingDocumentDTO.setIssuerBankIBAN(billingDocument.getIssuerBankIBAN());
        billingDocumentDTO.setIssuerBankOwner(billingDocument.getIssuerBankOwner());
        billingDocumentDTO.setVat1Percent(billingDocument.getVat1Percent());
        billingDocumentDTO.setVat1SumInEuro(billingDocument.getVat1SumInEuro());
        billingDocumentDTO.setVat2Percent(billingDocument.getVat2Percent());
        billingDocumentDTO.setVat2SumInEuro(billingDocument.getVat2SumInEuro());
        billingDocumentDTO.setNetAmountInEuro(billingDocument.getNetAmountInEuro());
        billingDocumentDTO.setGrossAmountInEuro(billingDocument.getGrossAmountInEuro());
        billingDocumentDTO.setClearingPeriodIdentifier(billingDocument.getClearingPeriodIdentifier());
        billingDocumentDTO.setBillingDocumentType(billingDocument.getBillingDocumentType());
        billingDocumentDTO.setBillingRunId(billingDocument.getBillingRun().getId());
        return billingDocumentDTO;
    }

    private BillingDocument mapToEntity(final BillingDocumentDTO billingDocumentDTO,
            final BillingDocument billingDocument) {
        billingDocument.setDocumentNumber(billingDocumentDTO.getDocumentNumber());
        billingDocument.setDocumentDate(billingDocumentDTO.getDocumentDate());
        billingDocument.setStatus(billingDocumentDTO.getStatus());
        billingDocument.setClearingPeriodType(billingDocumentDTO.getClearingPeriodType());
        billingDocument.setBeforeItemsText(billingDocumentDTO.getBeforeItemsText());
        billingDocument.setAfterItemsText(billingDocumentDTO.getAfterItemsText());
        billingDocument.setTermsText(billingDocumentDTO.getTermsText());
        billingDocument.setFooterText(billingDocumentDTO.getFooterText());
        billingDocument.setTenantId(billingDocumentDTO.getTenantId());
        billingDocument.setParticipantId(billingDocumentDTO.getParticipantId());
        billingDocument.setRecipientName(billingDocumentDTO.getRecipientName());
        billingDocument.setRecipientFirstname(billingDocumentDTO.getRecipientFirstname());
        billingDocument.setRecipientLastname(billingDocumentDTO.getRecipientLastname());
        billingDocument.setRecipientParticipantNumber(billingDocumentDTO.getRecipientParticipantNumber());
        billingDocument.setRecipientAddressLine1(billingDocumentDTO.getRecipientAddressLine1());
        billingDocument.setRecipientAddressLine2(billingDocumentDTO.getRecipientAddressLine2());
        billingDocument.setRecipientAddressLine3(billingDocumentDTO.getRecipientAddressLine3());
        billingDocument.setRecipientBankIban(billingDocumentDTO.getRecipientBankIban());
        billingDocument.setRecipientBankName(billingDocumentDTO.getRecipientBankName());
        billingDocument.setRecipientBankOwner(billingDocumentDTO.getRecipientBankOwner());
        billingDocument.setRecipientSepaMandateReference(billingDocumentDTO.getRecipientSepaMandateReference());
        billingDocument.setRecipientSepaMandateIssueDate(billingDocumentDTO.getRecipientSepaMandateIssueDate());
        billingDocument.setRecipientEmail(billingDocumentDTO.getRecipientEmail());
        billingDocument.setRecipientTaxId(billingDocumentDTO.getRecipientTaxId());
        billingDocument.setRecipientVatId(billingDocumentDTO.getRecipientVatId());
        billingDocument.setIssuerName(billingDocumentDTO.getIssuerName());
        billingDocument.setIssuerAddressLine1(billingDocumentDTO.getIssuerAddressLine1());
        billingDocument.setIssuerAddressLine2(billingDocumentDTO.getIssuerAddressLine2());
        billingDocument.setIssuerAddressLine3(billingDocumentDTO.getIssuerAddressLine3());
        billingDocument.setIssuerPhone(billingDocumentDTO.getIssuerPhone());
        billingDocument.setIssuerMail(billingDocumentDTO.getIssuerMail());
        billingDocument.setIssuerWebsite(billingDocumentDTO.getIssuerWebsite());
        billingDocument.setIssuerTaxId(billingDocumentDTO.getIssuerTaxId());
        billingDocument.setIssuerVatId(billingDocumentDTO.getIssuerVatId());
        billingDocument.setIssuerCompanyRegisterNumber(billingDocumentDTO.getIssuerCompanyRegisterNumber());
        billingDocument.setIssuerBankName(billingDocumentDTO.getIssuerBankName());
        billingDocument.setIssuerBankIBAN(billingDocumentDTO.getIssuerBankIBAN());
        billingDocument.setIssuerBankOwner(billingDocumentDTO.getIssuerBankOwner());
        billingDocument.setVat1Percent(billingDocumentDTO.getVat1Percent());
        billingDocument.setVat1SumInEuro(billingDocumentDTO.getVat1SumInEuro());
        billingDocument.setVat2Percent(billingDocumentDTO.getVat2Percent());
        billingDocument.setVat2SumInEuro(billingDocumentDTO.getVat2SumInEuro());
        billingDocument.setNetAmountInEuro(billingDocumentDTO.getNetAmountInEuro());
        billingDocument.setGrossAmountInEuro(billingDocumentDTO.getGrossAmountInEuro());
        billingDocument.setClearingPeriodIdentifier(billingDocumentDTO.getClearingPeriodIdentifier());
        billingDocument.setBillingDocumentType(billingDocumentDTO.getBillingDocumentType());
        final BillingRun billingRun = billingDocumentDTO.getBillingRunId() == null ? null : billingRunRepository.findById(billingDocumentDTO.getBillingRunId())
                .orElseThrow(() -> new NotFoundException("billingRunId not found"));
        billingDocument.setBillingRun(billingRun);
        return billingDocument;
    }

}
