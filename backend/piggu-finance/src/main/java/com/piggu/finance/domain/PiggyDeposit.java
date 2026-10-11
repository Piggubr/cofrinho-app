package com.piggu.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Deposito no cofrinho — uma linha da aba "Cofrinho". */
@Entity
@Table(name = "piggy_deposits")
public class PiggyDeposit {

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PiggyDeposit() {
    }

    public PiggyDeposit(LocalDate depositDate, BigDecimal amount, UUID userId) {
        this.id = UUID.randomUUID();
        this.depositDate = depositDate;
        this.amount = amount;
        this.userId = userId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
