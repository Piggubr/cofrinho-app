package com.piggu.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Preferencias de moeda da propria conta.
 *
 * @param moeda          moeda em que os valores aparecem (ISO 4217)
 * @param moedaConversao moeda para a qual a cotacao do topo converte
 * @param mostrarCotacao esconde ou mostra a cotacao no topo
 */
public record PreferencesRequest(
        @NotBlank String moeda,
        @NotBlank String moedaConversao,
        @NotNull Boolean mostrarCotacao
) {
}
