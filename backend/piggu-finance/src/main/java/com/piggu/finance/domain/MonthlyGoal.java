package com.piggu.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Limite de gasto de um mes. A chave e' o proprio mes no formato {@code AAAA-MM},
 * exatamente como a aba "Metas" usava.
 */
@Entity
@Table(name = "monthly_goals")
public class MonthlyGoal {

    @Id
    @Column(name = "reference_month", length = 7)
    private String referenceMonth;

    @Column(name = "limit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal limitAmount;

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected MonthlyGoal() {
    }

    public MonthlyGoal(String referenceMonth, BigDecimal limitAmount, String userEmail) {
        this.referenceMonth = referenceMonth;
        this.limitAmount = limitAmount;
        this.userEmail = userEmail;
    }

    public void atualizar(BigDecimal limitAmount, String userEmail) {
        this.limitAmount = limitAmount;
        this.userEmail = userEmail;
        this.updatedAt = Instant.now();
    }

    public String getReferenceMonth() {
        return referenceMonth;
    }

    public BigDecimal getLimitAmount() {
        return limitAmount;
    }
}
