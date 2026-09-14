package com.piggu.lifestyle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Filme na lista do casal. Uma linha da aba Filmes.
 *
 * <p>As notas de cada pessoa ficam em um mapa de e-mail para nota de 1 a 5, do
 * mesmo jeito que a coluna Avaliacoes_JSON guardava.</p>
 */
@Entity
@Table(name = "movies")
public class Movie {

    @Id
    private UUID id;

    @Column(name = "tmdb_id", nullable = false, length = 20)
    private String tmdbId = "";

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 4)
    private String year = "";

    @Column(nullable = false, length = 500)
    private String poster = "";

    @Column(name = "tmdb_rating", nullable = false, precision = 3, scale = 1)
    private BigDecimal tmdbRating = BigDecimal.ZERO;

    @Column(nullable = false, length = 1000)
    private String synopsis = "";

    @Column(nullable = false)
    private boolean watched;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Integer> ratings = new HashMap<>();

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Movie() {
    }

    public Movie(String tmdbId, String title, String year, String poster,
                 BigDecimal tmdbRating, String synopsis, String userEmail) {
        this.id = UUID.randomUUID();
        this.tmdbId = tmdbId;
        this.title = title;
        this.year = year;
        this.poster = poster;
        this.tmdbRating = tmdbRating;
        this.synopsis = synopsis;
        this.userEmail = userEmail;
    }

    @PrePersist
    void aoCriar() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public void marcarAssistido(boolean assistido) {
        this.watched = assistido;
    }

    /** Registra ou substitui a nota de uma pessoa. */
    public void avaliar(String email, int nota) {
        if (ratings == null) {
            ratings = new HashMap<>();
        }
        ratings.put(email, nota);
    }

    public UUID getId() {
        return id;
    }

    public String getTmdbId() {
        return tmdbId;
    }

    public String getTitle() {
        return title;
    }

    public String getYear() {
        return year;
    }

    public String getPoster() {
        return poster;
    }

    public BigDecimal getTmdbRating() {
        return tmdbRating;
    }

    public String getSynopsis() {
        return synopsis;
    }

    public boolean isWatched() {
        return watched;
    }

    public Map<String, Integer> getRatings() {
        return ratings == null ? Map.of() : ratings;
    }

    public String getUserEmail() {
        return userEmail;
    }
}
