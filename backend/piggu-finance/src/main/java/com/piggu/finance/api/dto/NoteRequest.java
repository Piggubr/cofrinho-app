package com.piggu.finance.api.dto;

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
        @NotBlank(message = "Digite o titulo da nota.")
        @Size(max = 150, message = "O titulo e longo demais.")
        String titulo,

        @Size(max = 1500, message = "O texto da nota e longo demais.")
        String texto,

        LocalDate data,

        @DecimalMin(value = "0", message = "O valor do evento e invalido.")
        @DecimalMax(value = "1000000", message = "O valor do evento e alto demais.")
        BigDecimal valor,

        @Size(max = 50)
        String categoria
) {
}
