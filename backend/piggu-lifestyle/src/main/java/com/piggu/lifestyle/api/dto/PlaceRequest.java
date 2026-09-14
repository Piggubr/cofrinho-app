package com.piggu.lifestyle.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Cadastro ou edicao de um lugar.
 *
 * @param marcacoes  marcadores; os desconhecidos sao descartados em silencio
 * @param imageBase64 foto opcional; quando presente substitui a anterior
 */
public record PlaceRequest(
        @NotBlank(message = "Digite o nome do lugar.")
        @Size(max = 120, message = "O nome do lugar e longo demais.")
        String nome,

        @Size(max = 50)
        String categoria,

        @Size(max = 200, message = "A localizacao e longa demais.")
        String localizacao,

        @NotNull(message = "A nota deve ser de 1 a 5.")
        @Min(value = 1, message = "A nota deve ser de 1 a 5.")
        @Max(value = 5, message = "A nota deve ser de 1 a 5.")
        Integer nota,

        @Size(max = 500, message = "O comentario e longo demais.")
        String comentario,

        @NotNull(message = "Escolha a data da visita.")
        LocalDate data,

        List<String> marcacoes,

        @DecimalMin(value = "0", message = "Digite um valor valido.")
        BigDecimal valor,

        String imageBase64,

        String mimeType
) {
}
