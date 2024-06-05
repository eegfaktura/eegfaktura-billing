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
@Getter
@Setter
public class FileData {

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue
    private UUID id;

    @Column
    private String tenantId;

    @Column
    private String name;

    @Column
    private String mimeType;

    @Lob
    private byte[] data;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private OffsetDateTime dateCreated;

    @LastModifiedDate
    @Column(nullable = false)
    private OffsetDateTime lastUpdated;

}
