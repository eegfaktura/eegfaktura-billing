package org.vfeeg.eegfaktura.billing.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;


@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class BillingDocumentItem {

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue
    private UUID id;

    @Column
    private String tenantId;

    @Column
    private String clearingPeriodType;

    @Column
    private String clearingPeriodIdentifier;

    @Column
    private BigDecimal amount;

    @Column
    private String meteringPointId;

    @Column
    private MeteringPointType meteringPointType;

    @Column
    private String text; // text for this very item

    @Column
    private String documentText; // text to be printed once on billing document

    @Column
    private String unit;

    @Column
    private BigDecimal pricePerUnit;

    @Column
    private String ppuUnit;

    @Column
    private BigDecimal netValue;

    @Column
    private BigDecimal discountPercent;

    @Column
    private BigDecimal vatPercent;

    @Column
    private BigDecimal vatValueInEuro;

    @Column
    private BigDecimal grossValue;

    @ManyToOne(fetch = FetchType.EAGER)
    private BillingDocument billingDocument;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private OffsetDateTime dateCreated;

    @LastModifiedDate
    @Column(nullable = false)
    private OffsetDateTime lastUpdated;

}
