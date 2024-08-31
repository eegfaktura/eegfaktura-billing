package org.vfeeg.eegfaktura.billing.model;

import lombok.Getter;
import lombok.Setter;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;


@Getter
@Setter
public class BillingDocumentDTO {

    private UUID id;
    private String documentNumber;
    private LocalDate documentDate;
    private String status;
    private String clearingPeriodType;
    private String beforeItemsText;
    private String afterItemsText;
    private String termsText;
    private String footerText;
    private String tenantId;
    private String participantId;
    private String recipientName;
    private String recipientFirstname;
    private String recipientLastname;
    private String recipientParticipantNumber;
    private String recipientAddressLine1;
    private String recipientAddressLine2;
    private String recipientAddressLine3;
    private String recipientBankName;
    private String recipientBankIban;
    private String recipientBankOwner;
    private String recipientSepaMandateReference;
    private LocalDate recipientSepaMandateIssueDate;
    private String recipientEmail;
    private String recipientTaxId;
    private String recipientVatId;
    private String issuerName;
    private String issuerAddressLine1;
    private String issuerAddressLine2;
    private String issuerAddressLine3;
    private String issuerPhone;
    private String issuerMail;
    private String issuerWebsite;
    private String issuerTaxId;
    private String issuerVatId;
    private String issuerCompanyRegisterNumber;
    private String issuerBankName;
    private String issuerBankIBAN;
    private String issuerBankOwner;
    private BigDecimal vat1Percent;
    private BigDecimal vat1SumInEuro;
    private BigDecimal vat2Percent;
    private BigDecimal vat2SumInEuro;
    private BigDecimal netAmountInEuro;
    private BigDecimal grossAmountInEuro;
    private String clearingPeriodIdentifier;
    private BillingDocumentType billingDocumentType;
    private UUID billingRunId;

}
