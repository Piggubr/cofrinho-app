package com.piggu.rewards.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Um lancamento de Fofocoins: positivo credita, negativo debita.
 *
 * <p>Nunca e alterado nem apagado. Corrigir um erro significa lancar o oposto,
 * para que o historico continue contando a verdade.</p>
 */
@Entity
@Table(name = "coin_ledger")
public class CoinEntry {

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    /** Dono das moedas. A planilha usava esta constante em todas as linhas. */
    public static final String USUARIA = "USUARIA";

    @Id
    private UUID id;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(name = "subject_user", nullable = false, length = 320)
    private String subjectUser = USUARIA;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CoinEntry() {
    }

    public CoinEntry(int amount, String reason, UUID actorId) {
        this.id = UUID.randomUUID();
        this.amount = amount;
        this.reason = reason;
        this.actorId = actorId;
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

    public int getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public String getSubjectUser() {
        return subjectUser;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
