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

/** Cofrinho por objetivo: quanto juntar e, se a familia quiser, ate quando. */
@Filter(name = DonoAtual.FILTRO, condition = DonoAtual.POR_USER_ID_OU_COMPARTILHADO, deduceAliasInjectionPoints = false)
@Entity
@Table(name = "savings_goals")
public class SavingsGoal {

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Id
    private UUID id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "target_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal targetAmount;

    private LocalDate deadline;

    /** Compartilhado com a familia: aparece para todos da casa, nao so para quem lancou. */
    @Column(nullable = false)
    private boolean shared;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SavingsGoal() {
    }

    public SavingsGoal(String name, BigDecimal targetAmount, LocalDate deadline, UUID userId) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.targetAmount = targetAmount;
        this.deadline = deadline;
        this.userId = userId;
    }

    @PrePersist
    void aoCriar() {
        createdAt = Instant.now();
    }

    public void alterar(String name, BigDecimal targetAmount, LocalDate deadline) {
        this.name = name;
        this.targetAmount = targetAmount;
        this.deadline = deadline;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** So quem lancou decide; quem chama confere ({@code CurrentUser#podeGerenciar}). */
    public void compartilhar(boolean compartilhado) {
        this.shared = compartilhado;
    }

    public boolean isShared() {
        return shared;
    }
}
