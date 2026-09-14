package com.piggu.lifestyle.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MovieRatingRequest(
        @NotNull(message = "Escolha uma nota de 1 a 5.")
        @Min(value = 1, message = "Escolha uma nota de 1 a 5.")
        @Max(value = 5, message = "Escolha uma nota de 1 a 5.")
        Integer nota
) {
}
