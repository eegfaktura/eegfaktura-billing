package org.vfeeg.eegfaktura.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * What a community recorded about the joining fee of one member (konzept-beitrittsgebuehr.md, extension
 * 27.10.2026): charge it in the next final run, or it was paid outside eegfaktura. One per (tenant, member).
 * The billed fee itself stays on the document (item type JOINING_FEE); a request is done once such a final
 * document exists. {@code note} is free text of the community - never logged.
 */
@Entity
@Table(name = "billing_joining_fee_record")
@Getter
@Setter
public class JoiningFeeRecord {

    public enum Kind { CHARGE_REQUESTED, PAID_EXTERNALLY }

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false, updatable = false)
    private String participantId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Kind kind;

    @Column
    private LocalDate paidOn;

    @Column
    private String note;

    @Column
    private String createdBy;

    @Column(nullable = false)
    private OffsetDateTime createdAt;
}
