package com.piggu.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Limite de gasto mensal de uma categoria. */
@Entity
@Table(name = "category_budgets")
public class CategoryBudget {

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Id
    private UUID id;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "limit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal limitAmount;

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CategoryBudget() {
    }

    public CategoryBudget(String category, BigDecimal limitAmount, String userEmail) {
        this.id = UUID.randomUUID();
        this.category = category;
        this.limitAmount = limitAmount;
        this.userEmail = userEmail;
    }

    @PrePersist
    void aoCriar() {
        createdAt = Instant.now();
    }

    public void alterarLimite(BigDecimal limitAmount, String userEmail) {
        this.limitAmount = limitAmount;
        this.userEmail = userEmail;
    }

    public UUID getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getLimitAmount() {
        return limitAmount;
    }
}
