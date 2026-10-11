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

/** Receita da familia: salario, renda extra, reembolso. */
@Entity
@Table(name = "incomes")
public class Income {

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Id
    private UUID id;

    @Column(name = "income_date", nullable = false)
    private LocalDate incomeDate;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Income() {
    }

    public Income(LocalDate incomeDate, String description, String category, BigDecimal amount, String userEmail) {
        this.id = UUID.randomUUID();
        this.incomeDate = incomeDate;
        this.description = description;
        this.category = category;
        this.amount = amount;
        this.userEmail = userEmail;
    }

    @PrePersist
    void aoCriar() {
        createdAt = Instant.now();
    }

    public void editar(LocalDate incomeDate, String description, String category, BigDecimal amount) {
        this.incomeDate = incomeDate;
        this.description = description;
        this.category = category;
        this.amount = amount;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getIncomeDate() {
        return incomeDate;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getUserEmail() {
        return userEmail;
    }
}
