package com.piggu.finance.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Edicao de um gasto: nome, categoria e valor, como no Apps Script, se ele e compartilhado e a data.
 *
 * @param compartilhado nulo mantem como esta; so quem lancou muda
 * @param data          nulo mantem a data do gasto
 */
public record UpdateExpenseRequest(
        @NotBlank(message = "Digite o nome do item.")
        @Size(max = 200)
        String item,

        @Size(max = 50)
        String categoria,

        @NotNull(message = "Digite o valor.")
        @DecimalMin(value = "0", message = "O valor nao pode ser negativo.")
        @DecimalMax(value = "1000000", message = "O valor e alto demais.")
        @Digits(integer = 7, fraction = 2, message = "O valor tem centavos demais.")
        BigDecimal valor,

        Boolean compartilhado,

        LocalDate data
) {

    public UpdateExpenseRequest(String item, String categoria, BigDecimal valor) {
        this(item, categoria, valor, null, null);
    }
}
