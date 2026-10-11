package com.piggu.lifestyle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Lugar visitado e avaliado. Uma linha da aba Lugares. */
@Entity
@Table(name = "places")
public class Place {

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 50)
    private String category = "Outros";

    @Column(nullable = false, length = 200)
    private String location = "";

    @Column(nullable = false)
    private short rating;

    @Column(nullable = false, length = 500)
    private String comment = "";

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> tags = new ArrayList<>();

    /** Identificador da foto no servico de media; nulo quando o lugar nao tem foto. */
    @Column(name = "photo_asset_id")
    private UUID photoAssetId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Place() {
    }

    public Place(String name, String category, String location, short rating, String comment,
                 LocalDate visitDate, List<String> tags, BigDecimal amount, String userEmail) {
        this.id = UUID.randomUUID();
        aplicar(name, category, location, rating, comment, visitDate, tags, amount);
        this.userEmail = userEmail;
    }

    @PrePersist
    void aoCriar() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public void aplicar(String name, String category, String location, short rating, String comment,
                        LocalDate visitDate, List<String> tags, BigDecimal amount) {
        this.name = name;
        this.category = category;
        this.location = location;
        this.rating = rating;
        this.comment = comment;
        this.visitDate = visitDate;
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
        this.amount = amount;
    }

    public void trocarFoto(UUID novoAssetId) {
        this.photoAssetId = novoAssetId;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getLocation() {
        return location;
    }

    public short getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public List<String> getTags() {
        return tags;
    }

    public UUID getPhotoAssetId() {
        return photoAssetId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
