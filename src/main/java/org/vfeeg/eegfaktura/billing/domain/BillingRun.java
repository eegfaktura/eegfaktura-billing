package org.vfeeg.eegfaktura.billing.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;


@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class BillingRun {

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue
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
