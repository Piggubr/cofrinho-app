package com.piggu.rewards.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PrizeRequest(
        @NotBlank(message = "Digite o nome do premio.")
        @Size(max = 100, message = "O nome do premio e longo demais.")
        String nome,

        @Size(max = 300, message = "A descricao e longa demais.")
        String descricao,

        @NotNull(message = "Digite um preco valido.")
        @Min(value = 1, message = "Digite um preco valido.")
        @Max(value = 10000000, message = "O preco e alto demais.")
        Integer preco,

        Boolean ativo
) {
}
