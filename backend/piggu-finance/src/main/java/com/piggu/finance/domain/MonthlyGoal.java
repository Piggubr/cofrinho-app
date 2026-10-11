package com.piggu.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Limite de gasto de um mes. Um registro por familia e mes ({@code AAAA-MM}),
 * o formato que a aba "Metas" usava.
 */
@Entity
@Table(name = "monthly_goals")
public class MonthlyGoal {

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Id
    private UUID id = UUID.randomUUID();

    /** Unico dentro da familia: cada familia tem a sua meta do mes. */
    @Column(name = "reference_month", nullable = false, length = 7)
    private String referenceMonth;

    @Column(name = "limit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal limitAmount;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected MonthlyGoal() {
    }

    public MonthlyGoal(String referenceMonth, BigDecimal limitAmount, UUID userId) {
        this.referenceMonth = referenceMonth;
        this.limitAmount = limitAmount;
        this.userId = userId;
    }

    public void atualizar(BigDecimal limitAmount, UUID userId) {
        this.limitAmount = limitAmount;
        this.userId = userId;
        this.updatedAt = Instant.now();
    }

    public String getReferenceMonth() {
        return referenceMonth;
    }

    public BigDecimal getLimitAmount() {
        return limitAmount;
    }
}
