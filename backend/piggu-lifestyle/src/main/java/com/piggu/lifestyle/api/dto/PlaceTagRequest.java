package com.piggu.lifestyle.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlaceTagRequest(
        @NotBlank(message = "Digite um nome valido.")
        @Size(min = 2, max = 50, message = "Digite um nome valido.")
        String nome
) {
}
