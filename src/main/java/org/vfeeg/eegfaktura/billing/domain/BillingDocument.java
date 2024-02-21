package org.vfeeg.eegfaktura.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;


@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class BillingDocument {

    @Id
    @Column(nullable = false, updatable = false)
    @GenericGenerator(name = "uuid", strategy = "org.hibernate.id.UUIDGenerator")
    @GeneratedValue(generator = "uuid")
    private UUID id;

    @Column
    private String documentNumber;

    @Column
    private LocalDate documentDate;

    @Column
    private String status;

    @Column
    private String clearingPeriodType;

    @Column
    private String beforeItemsText;

    @Column
    private String afterItemsText;

    @Column
    private String termsText;

    @Column
    private String footerText;

    @Column
    private String tenantId;

    @Column
    private String participantId;

    @Column
    private String recipientName;

    @Column
    private String recipientParticipantNumber;

    @Column
    private String recipientAddressLine1;

    @Column
    private String recipientAddressLine2;

    @Column
    private String recipientAddressLine3;

    @Column
    private String recipientBankName;

    @Column
    private String recipientBankIban;

    @Column
    private String recipientBankOwner;

    @Column
    private String recipientSepaMandateReference;

    @Column
    private LocalDate recipientSepaMandateIssueDate;

    @Column
    private String recipientEmail;

    @Column
    private String recipientTaxId;

    @Column
    private String recipientVatId;

    @Column
    private String issuerName;

    @Column
    private String issuerAddressLine1;

    @Column
    private String issuerAddressLine2;

    @Column
    private String issuerAddressLine3;

    @Column
    private String issuerPhone;

    @Column
    private String issuerMail;

    @Column
    private String issuerWebsite;

    @Column
    private String issuerTaxId;

    @Column
    private String issuerVatId;

    @Column
    private String issuerCompanyRegisterNumber;

    @Column
    private String issuerBankName;

    @Column
    private String issuerBankIBAN;

    @Column
    private String issuerBankOwner;

    @Column
    private BigDecimal vat1Percent;

    @Column
    private BigDecimal vat1SumInEuro;

    @Column
    private BigDecimal vat2Percent;

    @Column
    private BigDecimal vat2SumInEuro;

    @Column
    private BigDecimal netAmountInEuro;

    @Column
    private BigDecimal grossAmountInEuro;

    @Column
    private String clearingPeriodIdentifier;

    @Column
    private BillingDocumentType billingDocumentType;

    @ManyToOne(fetch = FetchType.LAZY)
    private BillingRun billingRun;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private OffsetDateTime dateCreated;

    @LastModifiedDate
    @Column(nullable = false)
    private OffsetDateTime lastUpdated;


    public static String getDocumentTypeName(BillingDocumentType billingDocumentType) {
        return switch(billingDocumentType) {
            case INVOICE -> "Rechnung";
            case CREDIT_NOTE, CREDIT_NOTE_RC -> "Gutschrift";
            case INFO -> "Information";
            default -> "Dokument";
        };
    }


}
