package com.piggu.media.domain;

import com.piggu.common.security.DonoAtual;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Foto do feed de um mes. Uma linha da aba Feed.
 *
 * <p>O mes fica no formato AAAA-MM. Na planilha essa coluna precisava ser formatada
 * como texto a cada gravacao, senao o Sheets convertia o valor em data.</p>
 */
@FilterDef(name = DonoAtual.FILTRO, autoEnabled = true, applyToLoadByKey = true, parameters = {
        @ParamDef(name = DonoAtual.TODOS, type = Boolean.class, resolver = DonoAtual.Todos.class),
        @ParamDef(name = DonoAtual.VISIVEIS, type = String.class, resolver = DonoAtual.Visiveis.class),
        @ParamDef(name = DonoAtual.SEM_DONO, type = Boolean.class, resolver = DonoAtual.SemDono.class),
        @ParamDef(name = DonoAtual.EU, type = String.class, resolver = DonoAtual.Eu.class)})
@Filter(name = DonoAtual.FILTRO, condition = DonoAtual.POR_USER_ID, deduceAliasInjectionPoints = false)
@Entity
@Table(name = "feed_photos")
public class FeedPhoto {

    /** Familia dona do registro: o Hibernate filtra as consultas e preenche ao gravar (ver FamiliaAtual). */
    @TenantId
    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Id
    private UUID id;

    @Column(name = "month_key", nullable = false, length = 7)
    private String monthKey;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(nullable = false, length = 300)
    private String caption = "";

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FeedPhoto() {
    }

    public FeedPhoto(String monthKey, UUID assetId, UUID userId) {
        this.id = UUID.randomUUID();
        this.monthKey = monthKey;
        this.assetId = assetId;
        this.userId = userId;
    }

    @PrePersist
    void aoCriar() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public void legendar(String caption) {
        this.caption = caption;
    }

    public UUID getId() {
        return id;
    }

    public String getMonthKey() {
        return monthKey;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public String getCaption() {
        return caption;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
