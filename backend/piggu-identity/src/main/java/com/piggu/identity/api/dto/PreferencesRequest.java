package com.piggu.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Preferencias de moeda da propria conta.
 *
 * @param moeda          moeda em que os valores aparecem (ISO 4217)
 * @param moedaConversao moeda para a qual a cotacao do topo converte
 * @param mostrarCotacao esconde ou mostra a cotacao no topo
 * @param fuso           fuso horario IANA (America/Sao_Paulo); nulo mantem o atual
 * @param moedaCotacao   outra ponta da cotacao do topo, separada da moeda dos valores;
 *                       nula mantem a atual
 */
public record PreferencesRequest(
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "Moeda invalida.") String moeda,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "Moeda invalida.") String moedaConversao,
        @NotNull Boolean mostrarCotacao,
        @Size(max = 50) String fuso,
        @Pattern(regexp = "[A-Z]{3}", message = "Moeda invalida.") String moedaCotacao
) {

    public PreferencesRequest(String moeda, String moedaConversao, Boolean mostrarCotacao) {
        this(moeda, moedaConversao, mostrarCotacao, null, null);
    }

    public PreferencesRequest(String moeda, String moedaConversao, Boolean mostrarCotacao, String fuso) {
        this(moeda, moedaConversao, mostrarCotacao, fuso, null);
    }
}
