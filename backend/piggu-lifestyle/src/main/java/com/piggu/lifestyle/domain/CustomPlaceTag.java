package com.piggu.lifestyle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Marcador de lugar criado pelo usuario: as linhas MARCADOR_LUGAR da aba Configuracoes. */
@Entity
@Table(name = "custom_place_tags")
public class CustomPlaceTag {

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "created_by_id")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected CustomPlaceTag() {
    }

    public CustomPlaceTag(String name, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.createdBy = createdBy;
    }

    public String getName() {
        return name;
    }
}
