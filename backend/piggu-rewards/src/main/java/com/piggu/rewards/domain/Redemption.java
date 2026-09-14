package com.piggu.rewards.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Resgate de um premio.
 *
 * <p>Guarda nome e preco do premio no momento do resgate: se o premio for renomeado
 * ou reprecificado depois, o historico continua mostrando o que de fato aconteceu.</p>
 */
@Entity
@Table(name = "redemptions")
public class Redemption {

    @Id
    private UUID id;

    @Column(name = "prize_id", nullable = false)
    private UUID prizeId;

    @Column(name = "prize_name", nullable = false, length = 100)
    private String prizeName;

    @Column(nullable = false)
    private int price;

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Column(nullable = false, length = 30)
    private String status = "Resgatado";

    @Column(name = "ledger_id")
    private UUID ledgerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Redemption() {
    }

    public Redemption(Prize premio, String userEmail, UUID ledgerId) {
        this.id = UUID.randomUUID();
        this.prizeId = premio.getId();
        this.prizeName = premio.getName();
        this.price = premio.getPrice();
        this.userEmail = userEmail;
        this.ledgerId = ledgerId;
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

    public String getPrizeName() {
        return prizeName;
    }

    public int getPrice() {
        return price;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
