package com.piggu.finance.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param data  dia do deposito; ausente significa hoje
 * @param valor valor em euro, sempre positivo
 */
public record DepositRequest(
        LocalDate data,

        @NotNull(message = "Digite um valor de deposito valido.")
        @DecimalMin(value = "0.01", message = "Digite um valor de deposito valido.")
        @DecimalMax(value = "10000000", message = "O valor do deposito e alto demais.")
        BigDecimal valor
) {
}
