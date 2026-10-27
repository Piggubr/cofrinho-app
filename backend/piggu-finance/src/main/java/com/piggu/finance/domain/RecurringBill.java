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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/** Conta fixa que se repete todo mes; com o tipo RECEITA, a receita que entra todo mes (o salario). */
@Filter(name = DonoAtual.FILTRO, condition = DonoAtual.POR_USER_ID_OU_COMPARTILHADO, deduceAliasInjectionPoints = false)
@Entity
@Table(name = "recurring_bills")
public class RecurringBill {

    public enum Tipo { GASTO, RECEITA }

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

    /** Segunda parcela da receita (o salario do dia 20 e do dia 5); nulos quando entra de uma vez. */
    @Column(name = "second_due_day")
    private Short secondDueDay;

    @Column(name = "second_amount", precision = 12, scale = 2)
    private BigDecimal secondAmount;

    @Column(name = "second_paid_month", length = 7)
    private String secondPaidMonth;

    /** Compartilhado com a familia: aparece para todos da casa, nao so para quem lancou. */
    @Column(nullable = false)
    private boolean shared;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Tipo kind = Tipo.GASTO;

    /** Cartao ou conta que paga o gasto todo mes; opcional. */
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RecurringBill() {
    }

    public RecurringBill(String description, String category, BigDecimal amount, int dueDay, boolean autoLaunch,
                         UUID userId) {
        this(Tipo.GASTO, description, category, amount, dueDay, autoLaunch, null, userId);
    }

    public RecurringBill(Tipo kind, String description, String category, BigDecimal amount, int dueDay,
                         boolean autoLaunch, UUID accountId, UUID userId) {
        this.id = UUID.randomUUID();
        this.kind = kind;
        this.userId = userId;
        this.accountId = accountId;
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

    /**
     * Divide em duas parcelas. A segunda comeca na proxima vez que o dia dela chegar: se ja
     * passou no mes de {@code desde}, so no mes seguinte.
     */
    public void dividir(int dia, BigDecimal valor, LocalDate desde) {
        this.secondDueDay = (short) dia;
        this.secondAmount = valor;
        if (dia <= desde.getDayOfMonth()) {
            this.secondPaidMonth = YearMonth.from(desde).toString();
        }
    }

    public LocalDate segundaParcelaEm(YearMonth mes) {
        return mes.atDay(Math.min(secondDueDay, mes.lengthOfMonth()));
    }

    public Short getSecondDueDay() {
        return secondDueDay;
    }

    public BigDecimal getSecondAmount() {
        return secondAmount;
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

    public UUID getUserId() {
        return userId;
    }

    public Tipo getKind() {
        return kind;
    }

    public UUID getAccountId() {
        return accountId;
    }

    /** So quem lancou decide; quem chama confere ({@code CurrentUser#podeGerenciar}). */
    public void compartilhar(boolean compartilhado) {
        this.shared = compartilhado;
    }

    public boolean isShared() {
        return shared;
    }
}
