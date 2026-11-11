package com.piggu.finance.api.dto;

import com.piggu.common.web.DataDeLancamento;
import com.piggu.common.web.Valores;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param data  dia do deposito; ausente significa hoje
 * @param valor     valor na moeda da conta, sempre positivo
 * @param objetivo  cofrinho por objetivo que recebe o deposito; ausente e o cofrinho geral
 */
public record DepositRequest(
        @DataDeLancamento
        LocalDate data,

        @NotNull(message = "Digite um valor de depósito válido.")
        @DecimalMin(value = Valores.MINIMO, message = Valores.ABAIXO_DO_MINIMO)
        @DecimalMax(value = Valores.MAXIMO_DE_ENTRADA, message = Valores.ACIMA_DO_MAXIMO_DE_ENTRADA)
        BigDecimal valor,

        UUID objetivo
) {

    public DepositRequest(LocalDate data, BigDecimal valor) {
        this(data, valor, null);
    }
}
