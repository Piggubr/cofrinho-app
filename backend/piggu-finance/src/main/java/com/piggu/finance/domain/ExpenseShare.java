package com.piggu.finance.domain;

import com.piggu.common.security.DonoAtual;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

/** Parte de uma pessoa da familia em um gasto dividido. */
@Filter(name = DonoAtual.FILTRO, condition = ExpenseShare.VISIVEL, deduceAliasInjectionPoints = false)
@Entity
@Table(name = "expense_shares")
public class ExpenseShare {

    /** A parte da pessoa, e todas as partes dos gastos que ela ve (por ser dona ou compartilhados). */
    static final String VISIVEL = "(:todos OR {alias}.member_id = ANY(CAST(:visiveis AS uuid[]))"
            + " OR {alias}.expense_id IN (SELECT e.id FROM expenses e"
            + " WHERE e.shared OR e.user_id = ANY(CAST(:visiveis AS uuid[])) OR (e.user_id IS NULL AND :semDono)))";


    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Id
    private UUID id;

    @Column(name = "expense_id", nullable = false)
    private UUID expenseId;

    @Column(name = "member_id")
    private UUID memberId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    protected ExpenseShare() {
    }

    public ExpenseShare(UUID expenseId, UUID memberId, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.expenseId = expenseId;
        this.memberId = memberId;
        this.amount = amount;
    }

    public UUID getMemberId() {
        return memberId;
    }
}
