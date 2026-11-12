package com.piggu.finance.api.dto;

import com.piggu.common.web.Valores;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Nova nota.
 *
 * <p>Quando valor e maior que zero a data passa a ser obrigatoria, porque a nota
 * vira tambem um gasto naquele dia.</p>
 */
public record NoteRequest(
        @NotBlank(message = "Digite o título da nota.")
        @Size(max = 150, message = "O título é longo demais.")
        String titulo,

        @Size(max = 1500, message = "O texto da nota é longo demais.")
        String texto,

        LocalDate data,

        @DecimalMin(value = Valores.MINIMO, message = Valores.ABAIXO_DO_MINIMO)
        @DecimalMax(value = Valores.MAXIMO, message = Valores.ACIMA_DO_MAXIMO)
        BigDecimal valor,

        @Size(max = 50)
        String categoria
) {
}
