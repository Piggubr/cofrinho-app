package com.piggu.finance.domain;

import com.piggu.common.security.DonoAtual;
import com.piggu.common.security.VisaoAtual;
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

/** Receita da familia: salario, renda extra, reembolso. */
@Filter(name = DonoAtual.FILTRO, condition = DonoAtual.POR_USER_ID_OU_COMPARTILHADO, deduceAliasInjectionPoints = false)
@Filter(name = VisaoAtual.FILTRO, condition = VisaoAtual.CONDICAO, deduceAliasInjectionPoints = false)
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

    /** Compartilhado com a familia: aparece para todos da casa, nao so para quem lancou. */
    @Column(nullable = false)
    private boolean shared;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Income() {
    }

    public Income(LocalDate incomeDate, String description, String category, BigDecimal amount, UUID userId) {
        this.id = UUID.randomUUID();
        this.incomeDate = incomeDate;
        this.description = description;
        this.category = category;
        this.amount = amount;
        this.userId = userId;
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

    public UUID getUserId() {
        return userId;
    }

    /** So quem lancou decide; quem chama confere ({@code CurrentUser#podeGerenciar}). */
    public void compartilhar(boolean compartilhado) {
        this.shared = compartilhado;
    }

    public boolean isShared() {
        return shared;
    }
}
