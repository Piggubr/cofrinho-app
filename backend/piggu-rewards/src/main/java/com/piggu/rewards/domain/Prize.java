package com.piggu.rewards.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Premio resgatavel com Fofocoins. Uma linha da aba Fofopremios. */
@Entity
@Table(name = "prizes")
public class Prize {

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

    @Column(name = "updated_by", nullable = false, length = 320)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Prize() {
    }

    public Prize(String name, String description, int price, boolean active, String updatedBy) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.price = price;
        this.active = active;
        this.updatedBy = updatedBy;
    }

    public void atualizar(String name, String description, int price, boolean active, String updatedBy) {
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
