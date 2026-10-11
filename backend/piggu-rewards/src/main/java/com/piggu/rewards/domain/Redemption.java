package com.piggu.rewards.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

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

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Id
    private UUID id;

    @Column(name = "prize_id", nullable = false)
    private UUID prizeId;

    @Column(name = "prize_name", nullable = false, length = 100)
    private String prizeName;

    @Column(nullable = false)
    private int price;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 30)
    private String status = "Resgatado";

    @Column(name = "ledger_id")
    private UUID ledgerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Redemption() {
    }

    public Redemption(Prize premio, UUID userId, UUID ledgerId) {
        this.id = UUID.randomUUID();
        this.prizeId = premio.getId();
        this.prizeName = premio.getName();
        this.price = premio.getPrice();
        this.userId = userId;
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

    public UUID getUserId() {
        return userId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
