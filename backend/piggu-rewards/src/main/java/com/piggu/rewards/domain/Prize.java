package com.piggu.rewards.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Premio resgatavel com Fofocoins. Uma linha da aba Fofopremios. */
@Entity
@Table(name = "prizes")
public class Prize {

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 300)
    private String description = "";

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "updated_by_id")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Prize() {
    }

    public Prize(String name, String description, int price, boolean active, UUID updatedBy) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.price = price;
        this.active = active;
        this.updatedBy = updatedBy;
    }

    public void atualizar(String name, String description, int price, boolean active, UUID updatedBy) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.active = active;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getPrice() {
        return price;
    }

    public boolean isActive() {
        return active;
    }
}
