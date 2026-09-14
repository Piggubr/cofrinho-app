package com.piggu.lifestyle.api.dto;

import java.math.BigDecimal;

/** Filme vindo do TMDB, ainda nao salvo na lista. */
public record TmdbMovie(
        String tmdbId,
        String titulo,
        String ano,
        String poster,
        BigDecimal nota,
        String sinopse
) {
}
