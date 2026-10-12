package com.piggu.lifestyle.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cadastro ou edicao de uma meta do mes.
 *
 * @param alvo  quanto precisa chegar para a meta ficar concluida
 * @param atual quanto ja foi feito; nulo vira zero
 */
public record PersonalGoalRequest(
        @NotBlank(message = "Digite a meta.")
        @Size(max = 80, message = "A meta e longa demais.")
        String titulo,

        @NotNull(message = "Diga quanto e o alvo da meta.")
        @Min(value = 1, message = "O alvo deve ser de 1 a 1.000.000.")
        @Max(value = 1_000_000, message = "O alvo deve ser de 1 a 1.000.000.")
        Integer alvo,

        @Min(value = 0, message = "O progresso deve ser de 0 a 1.000.000.")
        @Max(value = 1_000_000, message = "O progresso deve ser de 0 a 1.000.000.")
        Integer atual
) {
}
