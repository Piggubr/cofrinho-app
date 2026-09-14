package com.piggu.rewards.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

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

    /** Dono das moedas. A planilha usava esta constante em todas as linhas. */
    public static final String USUARIA = "USUARIA";

    /** Autor de lancamentos feitos pelo proprio sistema, como um resgate. */
    public static final String SISTEMA = "SISTEMA";

    @Id
    private UUID id;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(name = "subject_user", nullable = false, length = 320)
    private String subjectUser = USUARIA;

    @Column(name = "actor_email", nullable = false, length = 320)
    private String actorEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CoinEntry() {
    }

    public CoinEntry(int amount, String reason, String actorEmail) {
        this.id = UUID.randomUUID();
        this.amount = amount;
        this.reason = reason;
        this.actorEmail = actorEmail;
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
