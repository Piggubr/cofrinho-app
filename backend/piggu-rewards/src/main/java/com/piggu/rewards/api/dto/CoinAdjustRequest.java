package com.piggu.rewards.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Ajuste manual de Fofocoins, feito por um administrador.
 *
 * @param valor  quanto creditar ou debitar; zero nao faz sentido e e recusado
 * @param motivo obrigatorio, para que o historico continue legivel meses depois
 */
public record CoinAdjustRequest(
        @NotNull(message = "Digite uma quantidade válida de Fofocoins.")
        @Min(value = -1000000, message = "A quantidade é alta demais.")
        @Max(value = 1000000, message = "A quantidade é alta demais.")
        Integer valor,

        @NotBlank(message = "Explique o motivo do ajuste.")
        @Size(max = 200, message = "O motivo é longo demais.")
        String motivo
) {
}
