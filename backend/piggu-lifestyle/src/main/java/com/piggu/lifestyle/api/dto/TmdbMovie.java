package com.piggu.lifestyle.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Filme vindo do TMDB, ainda nao salvo na lista. */
public record TmdbMovie(
        @Size(max = 20) String tmdbId,
        @NotBlank(message = "O filme nao chegou corretamente.") @Size(max = 300) String titulo,
        @Size(max = 10) String ano,
        @Size(max = 500) String poster,
        @DecimalMin("0") @DecimalMax("10") BigDecimal nota,
        @Size(max = 5000) String sinopse
) {
}
