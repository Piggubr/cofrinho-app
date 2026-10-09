package com.piggu.finance.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 * @param contaId  conta ou cartao que pagou; opcional
 * @param parcelas 2 a 48 divide cada item em uma linha por mes; nulo ou 1 e a vista
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
        List<ExpenseItemRequest> itens,

        UUID contaId,

        @Min(value = 1, message = "Parcelas invalidas.")
        @Max(value = 48, message = "No maximo 48 parcelas.")
        Integer parcelas
) {

    /** Lancamento a vista, sem conta: o caso de quase todo o codigo interno. */
    public SaveExpensesRequest(LocalDate data, String estabelecimento, UUID reciboId, String origem,
                               List<ExpenseItemRequest> itens) {
        this(data, estabelecimento, reciboId, origem, itens, null, null);
    }
}
