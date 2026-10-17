package com.piggu.finance.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record MonthlyGoalRequest(
        @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "Mes invalido.")
        String mes,

        @NotNull(message = "Digite um valor valido para a meta.")
        @DecimalMin(value = "0.01", message = "Digite um valor valido para a meta.")
        @DecimalMax(value = "1000000", message = "O valor da meta e alto demais.")
        BigDecimal limite
) {
}
