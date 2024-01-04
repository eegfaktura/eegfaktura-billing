package org.vfeeg.eegfaktura.billing.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;


@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class BillingConfig {

    @Id
    @Column(nullable = false, updatable = false)
    @GenericGenerator(name = "uuid", strategy = "org.hibernate.id.UUIDGenerator")
    @GeneratedValue(generator = "uuid")
    private UUID id;

    @Column(nullable = false)
    private String tenantId;

    @Column
    private UUID headerImageFileDataId;

    @Column
    private UUID footerImageFileDataId;

    @Column
    private boolean isCreateCreditNotesForAllProducers;

    @Column
    private String beforeItemsTextInvoice;

    @Column
    private String beforeItemsTextCreditNote;

    @Column
    private String beforeItemsTextInfo;

    @Column
    private String afterItemsTextInvoice;

    @Column
    private String afterItemsTextCreditNote;

    @Column
    private String afterItemsTextInfo;

    @Column
    private String termsTextInvoice;

    @Column
    private String termsTextCreditNote;

    @Column
    private String termsTextInfo;

    @Column
    private String footerText;

    @Column
    private int documentNumberSequenceLength;

    @Column
    private UUID customTemplateFileDataId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private OffsetDateTime dateCreated;

    @LastModifiedDate
    @Column(nullable = false)
    private OffsetDateTime lastUpdated;

    @Column
    private String invoiceNumberPrefix;

    @Column
    private Long invoiceNumberStart;

    @Column
    private String creditNoteNumberPrefix;

    @Column
    private Long creditNoteNumberStart;

}
