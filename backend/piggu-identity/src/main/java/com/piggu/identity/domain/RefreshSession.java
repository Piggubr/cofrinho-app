package com.piggu.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Sessao longa do usuario. O token em si nunca e' gravado: guardamos apenas o
 * SHA-256 dele, para que um vazamento do banco nao permita assumir a sessao.
 */
@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Continuar conectado: o cookie sobrevive a fechar o navegador. */
    @Column(nullable = false)
    private boolean remember = true;

    protected RefreshSession() {
    }

    public RefreshSession(UUID userId, String tokenHash, String userAgent, Instant expiresAt) {
        this(userId, tokenHash, userAgent, expiresAt, true);
    }

    public RefreshSession(UUID userId, String tokenHash, String userAgent, Instant expiresAt, boolean remember) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.userAgent = userAgent;
        this.expiresAt = expiresAt;
        this.remember = remember;
    }

    public boolean isRemember() {
        return remember;
    }

    public boolean estaValida() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }

    public void revogar() {
        this.revokedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
