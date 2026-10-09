package com.piggu.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

/** Parte de uma pessoa da familia em um gasto dividido. */
@Entity
@Table(name = "expense_shares")
public class ExpenseShare {

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Id
    private UUID id;

    @Column(name = "expense_id", nullable = false)
    private UUID expenseId;

    @Column(name = "member_email", nullable = false, length = 320)
    private String memberEmail;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    protected ExpenseShare() {
    }

    public ExpenseShare(UUID expenseId, String memberEmail, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.expenseId = expenseId;
        this.memberEmail = memberEmail;
        this.amount = amount;
    }
}
