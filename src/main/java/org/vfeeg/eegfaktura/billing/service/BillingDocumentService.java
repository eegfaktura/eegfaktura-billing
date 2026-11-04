package org.vfeeg.eegfaktura.billing.service;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.vfeeg.eegfaktura.billing.domain.BillingDocument;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentDTO;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentRepository;
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

    public BillingDocumentService(final BillingDocumentRepository billingDocumentRepository) {
        this.billingDocumentRepository = billingDocumentRepository;
    }

    public List<BillingDocumentDTO> findByTenantIdAndYear(String tenantId, Integer year) {
        LocalDate instance = LocalDate.now(BillingService.BILLING_ZONE).withYear(year);
        LocalDate dateStart = instance.with(firstDayOfYear());
        LocalDate dateEnd = instance.with(lastDayOfYear());

        final List<BillingDocument> billingDocuments =
                billingDocumentRepository.findByTenantIdAndDocumentDateBetweenAndBillingRunIsNotNull(
                        tenantId, dateStart, dateEnd);
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
        billingDocumentDTO.setRecipientSepaDirectDebit(billingDocument.getRecipientSepaDirectDebit());
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
        billingDocumentDTO.setIssuerBankCreditorId(billingDocument.getIssuerBankCreditorId());
        billingDocumentDTO.setVat1Percent(billingDocument.getVat1Percent());
        billingDocumentDTO.setVat1SumInEuro(billingDocument.getVat1SumInEuro());
        billingDocumentDTO.setVat2Percent(billingDocument.getVat2Percent());
        billingDocumentDTO.setVat2SumInEuro(billingDocument.getVat2SumInEuro());
        billingDocumentDTO.setNetAmountInEuro(billingDocument.getNetAmountInEuro());
        billingDocumentDTO.setGrossAmountInEuro(billingDocument.getGrossAmountInEuro());
        billingDocumentDTO.setClearingPeriodIdentifier(billingDocument.getClearingPeriodIdentifier());
        billingDocumentDTO.setBillingDocumentType(billingDocument.getBillingDocumentType());
        // rows older releases left without a run (known-errors #13)
        billingDocumentDTO.setBillingRunId(billingDocument.getBillingRun() == null ? null
                : billingDocument.getBillingRun().getId());
        return billingDocumentDTO;
    }

}
