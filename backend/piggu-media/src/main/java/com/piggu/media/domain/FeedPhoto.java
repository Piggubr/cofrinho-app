package com.piggu.media.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Foto do feed de um mes. Uma linha da aba Feed.
 *
 * <p>O mes fica no formato AAAA-MM. Na planilha essa coluna precisava ser formatada
 * como texto a cada gravacao, senao o Sheets convertia o valor em data.</p>
 */
@Entity
@Table(name = "feed_photos")
public class FeedPhoto {

    @Id
    private UUID id;

    @Column(name = "month_key", nullable = false, length = 7)
    private String monthKey;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(nullable = false, length = 300)
    private String caption = "";

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FeedPhoto() {
    }

    public FeedPhoto(String monthKey, UUID assetId, String userEmail) {
        this.id = UUID.randomUUID();
        this.monthKey = monthKey;
        this.assetId = assetId;
        this.userEmail = userEmail;
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

    public String getUserEmail() {
        return userEmail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
