package com.piggu.banking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Banco conectado por um usuario — um "item" na Pluggy. */
@Entity
@Table(name = "bank_connections")
public class BankConnection {

    @Id
    private UUID id;

    @Column(name = "pluggy_item_id", nullable = false, unique = true, length = 100)
    private String pluggyItemId;

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

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

    public BankConnection(String pluggyItemId, String userEmail) {
        this.id = UUID.randomUUID();
        this.pluggyItemId = pluggyItemId;
        this.userEmail = userEmail;
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

    public String getUserEmail() {
        return userEmail;
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
