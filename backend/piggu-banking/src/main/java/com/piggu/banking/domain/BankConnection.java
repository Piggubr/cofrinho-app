package com.piggu.banking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Banco conectado por um usuario — um "item" na Pluggy. */
@Entity
@Table(name = "bank_connections")
public class BankConnection {

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Id
    private UUID id;

    @Column(name = "pluggy_item_id", nullable = false, unique = true, length = 100)
    private String pluggyItemId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 200)
    private String institution = "";

    @Column(nullable = false, length = 50)
    private String status = "";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "synced_at")
    private Instant syncedAt;

    protected BankConnection() {
    }

    public BankConnection(String pluggyItemId, UUID userId) {
        this.id = UUID.randomUUID();
        this.pluggyItemId = pluggyItemId;
        this.userId = userId;
    }

    @PrePersist
    void aoCriar() {
        createdAt = Instant.now();
    }

    public void sincronizado(String institution, String status) {
        this.institution = institution;
        this.status = status;
        this.syncedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getPluggyItemId() {
        return pluggyItemId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getInstitution() {
        return institution;
    }

    public String getStatus() {
        return status;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
