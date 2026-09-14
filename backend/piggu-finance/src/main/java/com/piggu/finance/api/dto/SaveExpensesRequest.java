package com.piggu.finance.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lancamento de gastos: um ou varios itens da mesma data e do mesmo estabelecimento.
 *
 * <p>Substitui a acao save. O limite de 100 itens e o mesmo de validarItens_.</p>
 *
 * @param reciboId agrupa os itens vindos de uma mesma foto; nulo gera um novo
 * @param origem   Manual, Foto ou Nota
 */
public record SaveExpensesRequest(
        @NotNull(message = "A data do gasto e invalida.")
        LocalDate data,

        @Size(max = 200)
        String estabelecimento,

        UUID reciboId,

        @Size(max = 30)
        String origem,

        @NotEmpty(message = "A lista de gastos esta vazia.")
        @Size(max = 100, message = "A lista de gastos e grande demais.")
        @Valid
        List<ExpenseItemRequest> itens
) {
}
