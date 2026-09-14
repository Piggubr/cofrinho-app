package com.piggu.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Categoria criada pelo usuario: as linhas CATEGORIA da aba Configuracoes. */
@Entity
@Table(name = "custom_categories")
public class CustomCategory {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "created_by", nullable = false, length = 320)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected CustomCategory() {
    }

    public CustomCategory(String name, String createdBy) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
