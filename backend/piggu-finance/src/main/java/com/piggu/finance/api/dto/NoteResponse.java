package com.piggu.finance.api.dto;

import com.piggu.finance.domain.Note;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record NoteResponse(
        UUID id,
        String titulo,
        String texto,
        LocalDate data,
        BigDecimal valor,
        String categoria,
        UUID gastoId,
        String usuario,
        Instant criadoEm
) {

    public static NoteResponse de(Note nota) {
        return new NoteResponse(
                nota.getId(),
                nota.getTitle(),
                nota.getBody(),
                nota.getNoteDate(),
                nota.getAmount(),
                nota.getCategory(),
                nota.getExpenseId(),
                nota.getUserEmail(),
                nota.getCreatedAt()
        );
    }
}
