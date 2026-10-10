package com.piggu.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Conta ou cartao de credito da familia. */
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

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentAccount() {
    }

    public PaymentAccount(String name, Tipo kind, Short closingDay, Short dueDay, String userEmail) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.kind = kind;
        this.closingDay = closingDay;
        this.dueDay = dueDay;
        this.userEmail = userEmail;
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

    public Short getDueDay() {
        return dueDay;
    }
}
