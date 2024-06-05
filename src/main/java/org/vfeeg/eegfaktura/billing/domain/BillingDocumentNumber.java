package org.vfeeg.eegfaktura.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;


@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(uniqueConstraints = { @UniqueConstraint(columnNames = { "tenantId", "documentNumber" }) })
@Getter
@Setter
public class BillingDocumentNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column
    private String tenantId;

    @Column
    private BillingDocumentType documentType;

    @Column
    private int year;

    @Column
    private String prefix;

    @Column
    private Long sequenceNumber;

    @Column
    private String documentNumber;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private OffsetDateTime dateCreated;

    @LastModifiedDate
    @Column(nullable = false)
    private OffsetDateTime lastUpdated;

}
