package com.piggu.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Convite do titular para alguem entrar na familia como membro. */
@Entity
@Table(name = "household_invites")
public class HouseholdInvite {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected HouseholdInvite() {
    }

    public HouseholdInvite(UUID householdId, String email, UUID invitedBy, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.householdId = householdId;
        this.email = email;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void aoCriar() {
        createdAt = Instant.now();
    }

    public boolean valido() {
        return expiresAt.isAfter(Instant.now());
    }

    public void renovar(Instant novoVencimento) {
        this.expiresAt = novoVencimento;
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public String getEmail() {
        return email;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
