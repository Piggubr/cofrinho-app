package com.piggu.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Um item de gasto — uma linha da aba "Gastos".
 *
 * <p>Cada item de um recibo vira um registro proprio; {@code receiptId} e' o que
 * mantem juntos os itens lidos da mesma foto.</p>
 */
@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    private UUID id;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "receipt_id", nullable = false)
    private UUID receiptId;

    @Column(nullable = false, length = 200)
    private String merchant = "";

    @Column(nullable = false, length = 200)
    private String item;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 30)
    private String kind = "Variavel";

    @Column(nullable = false, length = 30)
    private String source = "Manual";

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Expense() {
    }

    public Expense(LocalDate expenseDate, UUID receiptId, String merchant, String item,
                   String category, BigDecimal amount, String kind, String source, String userEmail) {
        this.id = UUID.randomUUID();
        this.expenseDate = expenseDate;
        this.receiptId = receiptId;
        this.merchant = merchant;
        this.item = item;
        this.category = category;
        this.amount = amount;
        this.kind = kind;
        this.source = source;
        this.userEmail = userEmail;
    }

    @PrePersist
    void aoCriar() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    /** Edicao pela tela de gastos: so nome, categoria e valor mudam, como no Apps Script. */
    public void editar(String item, String category, BigDecimal amount) {
        this.item = item;
        this.category = category;
        this.amount = amount;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public UUID getReceiptId() {
        return receiptId;
    }

    public String getMerchant() {
        return merchant;
    }

    public String getItem() {
        return item;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getKind() {
        return kind;
    }

    public String getSource() {
        return source;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
