package com.piggu.finance.domain;

import com.piggu.common.security.DonoAtual;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Deposito no cofrinho — uma linha da aba "Cofrinho". */
@Filter(name = DonoAtual.FILTRO, condition = PiggyDeposit.VISIVEL, deduceAliasInjectionPoints = false)
@Entity
@Table(name = "piggy_deposits")
public class PiggyDeposit {

    /** O de quem a pessoa ve e todo deposito num cofrinho compartilhado (a meta a dois). */
    static final String VISIVEL = "(" + DonoAtual.POR_USER_ID + " OR {alias}.goal_id IN"
            + " (SELECT g.id FROM savings_goals g WHERE g.shared))";

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Id
    private UUID id;

    @Column(name = "deposit_date", nullable = false)
    private LocalDate depositDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "user_id")
    private UUID userId;

    /** Objetivo para o qual o deposito foi; nulo e o cofrinho geral. */
    @Column(name = "goal_id")
    private UUID goalId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PiggyDeposit() {
    }

    public PiggyDeposit(LocalDate depositDate, BigDecimal amount, UUID userId) {
        this(depositDate, amount, userId, null);
    }

    public PiggyDeposit(LocalDate depositDate, BigDecimal amount, UUID userId, UUID goalId) {
        this.id = UUID.randomUUID();
        this.depositDate = depositDate;
        this.amount = amount;
        this.userId = userId;
        this.goalId = goalId;
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

    public LocalDate getDepositDate() {
        return depositDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getGoalId() {
        return goalId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
