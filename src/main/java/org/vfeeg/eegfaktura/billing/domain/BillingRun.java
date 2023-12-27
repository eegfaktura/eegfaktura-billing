package org.vfeeg.eegfaktura.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.time.LocalDateTime;
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
public class BillingRun {

    @Id
    @Column(nullable = false, updatable = false)
    @GenericGenerator(name = "uuid", strategy = "org.hibernate.id.UUIDGenerator")
    @GeneratedValue(generator = "uuid")
    private UUID id;

    @Column
    private String clearingPeriodType;

    @Column
    private String clearingPeriodIdentifier;

    @Column
    private String tenantId;

    @Column
    private BillingRunStatus runStatus;

    @Column
    private LocalDateTime runStatusDateTime;

    @Column
    private String mailStatus;

    @Column
    private LocalDateTime mailStatusDateTime;

    @Column
    private String sepaStatus;

    @Column
    private LocalDateTime sepaStatusDateTime;

    @Column
    private Integer numberOfInvoices;

    @Column
    private Integer numberOfCreditNotes;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private OffsetDateTime dateCreated;

    @LastModifiedDate
    @Column(nullable = false)
    private OffsetDateTime lastUpdated;

}
