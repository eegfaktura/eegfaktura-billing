package org.vfeeg.eegfaktura.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A file (image) uploaded for the mail text of one tenant and mail key. The bytes, the file name
 * and the detected MIME type are in {@link FileData}.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class BillingMailTemplateFile {

    public static final String KIND_IMAGE = "image";
    public static final String KIND_ATTACHMENT = "attachment";

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false, updatable = false)
    private String mailKey;

    @Column(nullable = false, updatable = false)
    private UUID fileDataId;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private int size;

    /** False for an upload (draft, never sent); true once a saved version uses the file. */
    @Column(nullable = false)
    private boolean committed;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private OffsetDateTime dateCreated;
}
