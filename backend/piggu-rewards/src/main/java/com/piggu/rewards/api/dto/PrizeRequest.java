package com.piggu.rewards.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PrizeRequest(
        @NotBlank(message = "Digite o nome do prêmio.")
        @Size(max = 100, message = "O nome do prêmio é longo demais.")
        String nome,

        @Size(max = 300, message = "A descrição é longa demais.")
        String descricao,

        @NotNull(message = "Digite um preço válido.")
        @Min(value = 1, message = "Digite um preço válido.")
        @Max(value = 10000000, message = "O preço é alto demais.")
        Integer preco,

        Boolean ativo
) {
}
