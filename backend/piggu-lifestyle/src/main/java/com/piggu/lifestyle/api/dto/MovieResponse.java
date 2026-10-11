package com.piggu.lifestyle.api.dto;

import com.piggu.lifestyle.domain.Movie;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record MovieResponse(
        UUID id,
        String tmdbId,
        String titulo,
        String ano,
        String poster,
        BigDecimal nota,
        String sinopse,
        boolean assistido,
        Map<String, Integer> avaliacoes,
        UUID usuario
) {

    public static MovieResponse de(Movie filme) {
        return new MovieResponse(
                filme.getId(),
                filme.getTmdbId(),
                filme.getTitle(),
                filme.getYear(),
                filme.getPoster(),
                filme.getTmdbRating(),
                filme.getSynopsis(),
                filme.isWatched(),
                filme.getRatings(),
                filme.getUserId()
        );
    }
}
