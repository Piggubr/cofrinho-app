package com.piggu.finance.api.dto;

import com.piggu.common.web.Valores;
import com.piggu.common.web.Meses;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record MonthlyGoalRequest(
        @NotBlank(message = "Informe o mês da meta.")
        @Pattern(regexp = Meses.FORMATO, message = Meses.INVALIDO)
        String mes,

        @NotNull(message = "Digite um valor válido para a meta.")
        @DecimalMin(value = Valores.MINIMO, message = Valores.ABAIXO_DO_MINIMO)
        @DecimalMax(value = Valores.MAXIMO, message = Valores.ACIMA_DO_MAXIMO)
        BigDecimal limite
) {
}
