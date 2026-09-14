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
 * Lembrete da aba Notas.
 *
 * <p>Uma nota com valor maior que zero representa um evento pago: ao ser criada
 * ela tambem gera um gasto, e {@code expenseId} guarda esse vinculo.</p>
 */
@Entity
@Table(name = "notes")
public class Note {

    @Id
    private UUID id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 1500)
    private String body = "";

    @Column(name = "note_date")
    private LocalDate noteDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(nullable = false, length = 50)
    private String category = "";

    @Column(name = "expense_id")
    private UUID expenseId;

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Note() {
    }

    public Note(String title, String body, LocalDate noteDate, BigDecimal amount,
                String category, UUID expenseId, String userEmail) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.body = body;
        this.noteDate = noteDate;
        this.amount = amount;
        this.category = category;
        this.expenseId = expenseId;
        this.userEmail = userEmail;
    }

    @PrePersist
    void aoCriar() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public LocalDate getNoteDate() {
        return noteDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCategory() {
        return category;
    }

    public UUID getExpenseId() {
        return expenseId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
