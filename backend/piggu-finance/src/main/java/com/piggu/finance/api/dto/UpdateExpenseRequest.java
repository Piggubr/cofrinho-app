package com.piggu.finance.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Edicao de um gasto: apenas nome, categoria e valor, como no Apps Script. */
public record UpdateExpenseRequest(
        @NotBlank(message = "Digite o nome do item.")
        @Size(max = 200)
        String item,

        @Size(max = 50)
        String categoria,

        @NotNull(message = "Digite o valor.")
        @DecimalMin(value = "0", message = "O valor nao pode ser negativo.")
        @DecimalMax(value = "1000000", message = "O valor e alto demais.")
        BigDecimal valor
) {
}
