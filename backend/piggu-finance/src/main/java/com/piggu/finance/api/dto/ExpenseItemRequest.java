package com.piggu.finance.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Um item dentro de um lancamento de gastos.
 *
 * @param item      nome do produto ou servico
 * @param categoria categoria; se nao for uma das conhecidas, o servidor usa Outros
 * @param valor     valor em euro
 * @param tipo      Fixo ou Variavel
 */
public record ExpenseItemRequest(
        @NotBlank(message = "Existe um item sem nome.")
        @Size(max = 200, message = "O nome do item e longo demais.")
        String item,

        @Size(max = 50)
        String categoria,

        @NotNull(message = "Existe um item sem valor.")
        @DecimalMin(value = "0", message = "O valor nao pode ser negativo.")
        @DecimalMax(value = "1000000", message = "O valor e alto demais.")
        BigDecimal valor,

        @Size(max = 30)
        String tipo
) {
}
