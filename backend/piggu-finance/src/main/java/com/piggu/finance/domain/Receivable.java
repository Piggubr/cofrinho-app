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

/** Dinheiro que alguem deve para a pessoa; so vira receita quando ela marca que recebeu. */
@Filter(name = DonoAtual.FILTRO, condition = DonoAtual.POR_USER_ID, deduceAliasInjectionPoints = false)
@Entity
@Table(name = "receivables")
public class Receivable {

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String debtor;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "income_id")
    private UUID incomeId;

    @Column(name = "received_at")
    private LocalDate receivedAt;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Receivable() {
    }

    public Receivable(String debtor, String description, BigDecimal amount, LocalDate dueDate, UUID userId) {
        this.id = UUID.randomUUID();
        this.debtor = debtor;
        this.description = description;
        this.amount = amount;
        this.dueDate = dueDate;
        this.userId = userId;
    }

    @PrePersist
    void aoCriar() {
        createdAt = Instant.now();
    }

    public void recebida(LocalDate dia, UUID receita) {
        this.receivedAt = dia;
        this.incomeId = receita;
    }

    public boolean isRecebida() {
        return receivedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public String getDebtor() {
        return debtor;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getReceivedAt() {
        return receivedAt;
    }
}
