package com.piggu.lifestyle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Algo que a pessoa decidiu nao comprar; o valor conta como gasto evitado. So a dona ve. */
@Entity
@Table(name = "girls_math_items")
public class GirlsMathItem {

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String product;

    @Column(nullable = false, length = 120)
    private String store = "";

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "item_date", nullable = false)
    private LocalDate itemDate;

    /** Identificador da foto no servico de media; nulo quando o item nao tem foto. */
    @Column(name = "photo_asset_id")
    private UUID photoAssetId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GirlsMathItem() {
    }

    public GirlsMathItem(String product, String store, BigDecimal amount, LocalDate itemDate, UUID userId) {
        this.id = UUID.randomUUID();
        this.product = product;
        this.store = store;
        this.amount = amount;
        this.itemDate = itemDate;
        this.userId = userId;
    }

    @PrePersist
    void aoCriar() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public void trocarFoto(UUID novoAssetId) {
        this.photoAssetId = novoAssetId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getProduct() {
        return product;
    }

    public String getStore() {
        return store;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getItemDate() {
        return itemDate;
    }

    public UUID getPhotoAssetId() {
        return photoAssetId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
