package org.vfeeg.eegfaktura.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The stored mail text of one tenant for one mail key (konzept-mail-texte-editierbar.md).
 * {@code version} is the edit stamp the API compares on PUT (0 = nothing stored);
 * {@code defaultVersion} is the catalog default the text was based on.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class BillingMailTemplate {

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false, updatable = false)
    private String mailKey;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String bodyHtml;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private int defaultVersion;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private OffsetDateTime dateCreated;

    @LastModifiedDate
    @Column(nullable = false)
    private OffsetDateTime lastUpdated;

    @Column
    private String updatedBy;
}
