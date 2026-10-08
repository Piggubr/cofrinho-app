package com.piggu.media.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Um arquivo guardado.
 *
 * <p>O registro vive no banco e os bytes no Google Drive. O front nunca ve o id do
 * Drive: ele trabalha com o id do asset, e trocar de provedor de armazenamento no
 * futuro nao muda nada do lado de fora.</p>
 */
@Entity
@Table(name = "assets")
public class Asset {

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    public static final String CONTEXTO_PADRAO = "GERAL";

    @Id
    private UUID id;

    @Column(name = "drive_file_id", nullable = false, length = 200)
    private String driveFileId;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 30)
    private String context = CONTEXTO_PADRAO;

    @Column(name = "owner_email", nullable = false, length = 320)
    private String ownerEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Asset() {
    }

    public Asset(String driveFileId, String contentType, long sizeBytes, String context, String ownerEmail) {
        this.id = UUID.randomUUID();
        this.driveFileId = driveFileId;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.context = context;
        this.ownerEmail = ownerEmail;
    }

    @PrePersist
    void aoCriar() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getDriveFileId() {
        return driveFileId;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getContext() {
        return context;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
