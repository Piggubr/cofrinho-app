package com.piggu.finance.api.dto;

import com.piggu.common.web.Valores;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Um item dentro de um lancamento de gastos.
 *
 * @param item      nome do produto ou servico
 * @param categoria categoria; se nao for uma das conhecidas, o servidor usa Outros
 * @param valor     valor na moeda da conta
 * @param tipo      Fixo ou Variavel
 * @param moedaOriginal codigo ISO da moeda da compra, quando nao e a da pessoa; o valor ja vem convertido
 * @param valorOriginal valor na moeda da compra
 */
public record ExpenseItemRequest(
        @NotBlank(message = "Existe um item sem nome.")
        @Size(max = 200, message = "O nome do item e longo demais.")
        String item,

        @Size(max = 50)
        String categoria,

        @NotNull(message = "Existe um item sem valor.")
        @DecimalMin(value = Valores.MINIMO, message = Valores.ABAIXO_DO_MINIMO)
        @DecimalMax(value = Valores.MAXIMO, message = Valores.ACIMA_DO_MAXIMO)
        @Digits(integer = 7, fraction = 2, message = "O valor tem centavos demais.")
        BigDecimal valor,

        @Size(max = 30)
        String tipo,

        @Pattern(regexp = "[A-Z]{3}", message = "Moeda invalida.")
        String moedaOriginal,

        @DecimalMin(value = Valores.MINIMO, message = Valores.ABAIXO_DO_MINIMO)
        @DecimalMax(value = Valores.MAXIMO, message = Valores.ACIMA_DO_MAXIMO)
        BigDecimal valorOriginal
) {

    public ExpenseItemRequest(String item, String categoria, BigDecimal valor, String tipo) {
        this(item, categoria, valor, tipo, null, null);
    }
}
