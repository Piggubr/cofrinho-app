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
import java.time.YearMonth;
import java.util.UUID;

/** Conta fixa que se repete todo mes. */
@Entity
@Table(name = "recurring_bills")
public class RecurringBill {

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_day", nullable = false)
    private short dueDay;

    @Column(name = "auto_launch", nullable = false)
    private boolean autoLaunch;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_paid_month", length = 7)
    private String lastPaidMonth;

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RecurringBill() {
    }

    public RecurringBill(String description, String category, BigDecimal amount, int dueDay, boolean autoLaunch,
                         String userEmail) {
        this.id = UUID.randomUUID();
        this.userEmail = userEmail;
        editar(description, category, amount, dueDay, autoLaunch);
    }

    @PrePersist
    void aoCriar() {
        createdAt = Instant.now();
    }

    public void editar(String description, String category, BigDecimal amount, int dueDay, boolean autoLaunch) {
        this.description = description;
        this.category = category;
        this.amount = amount;
        this.dueDay = (short) dueDay;
        this.autoLaunch = autoLaunch;
    }

    /** Vencimento no mes; dia 31 em fevereiro vira o ultimo dia do mes. */
    public LocalDate vencimentoEm(YearMonth mes) {
        return mes.atDay(Math.min(dueDay, mes.lengthOfMonth()));
    }

    public boolean pagaEm(YearMonth mes) {
        return lastPaidMonth != null && lastPaidMonth.compareTo(mes.toString()) >= 0;
    }

    public void marcarPaga(YearMonth mes) {
        if (!pagaEm(mes)) {
            this.lastPaidMonth = mes.toString();
        }
    }

    public UUID getId() {
        return id;
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

    public int getDueDay() {
        return dueDay;
    }

    public boolean isAutoLaunch() {
        return autoLaunch;
    }

    public String getUserEmail() {
        return userEmail;
    }
}
