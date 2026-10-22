package com.piggu.finance.domain;

import com.piggu.common.security.DonoAtual;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Conta ou cartao de credito da familia. */
@Filter(name = DonoAtual.FILTRO, condition = DonoAtual.POR_USER_ID_OU_COMPARTILHADO, deduceAliasInjectionPoints = false)
@Entity
@Table(name = "payment_accounts")
public class PaymentAccount {

    public enum Tipo { CONTA, CARTAO }

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Id
    private UUID id;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Tipo kind;

    @Column(name = "closing_day")
    private Short closingDay;

    @Column(name = "due_day")
    private Short dueDay;

    /** Compartilhado com a familia: aparece para todos da casa, nao so para quem lancou. */
    @Column(nullable = false)
    private boolean shared;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentAccount() {
    }

    public PaymentAccount(String name, Tipo kind, Short closingDay, Short dueDay, UUID userId) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.kind = kind;
        this.closingDay = closingDay;
        this.dueDay = dueDay;
        this.userId = userId;
    }

    @PrePersist
    void aoCriar() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Tipo getKind() {
        return kind;
    }

    public Short getClosingDay() {
        return closingDay;
    }

    public UUID getUserId() {
        return userId;
    }

    public Short getDueDay() {
        return dueDay;
    }

    /** So quem lancou decide; quem chama confere ({@code CurrentUser#podeGerenciar}). */
    public void compartilhar(boolean compartilhado) {
        this.shared = compartilhado;
    }

    public boolean isShared() {
        return shared;
    }
}
