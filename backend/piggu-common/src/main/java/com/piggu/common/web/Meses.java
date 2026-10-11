package com.piggu.common.web;

import com.piggu.common.error.BusinessException;
import org.springframework.http.HttpStatus;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Mes no formato AAAA-MM vindo de parametro de URL.
 *
 * <p>O @Pattern do DTO so cobre o corpo; parametro de query cai direto no parse e
 * viraria 500. Aqui vira 400 com mensagem.</p>
 */
public final class Meses {

    /** O Piggu e brasileiro primeiro: "este mes" e o de Brasilia. */
    public static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    private Meses() {
    }

    /** Mes informado, ou o atual (Brasilia) quando vazio. */
    public static YearMonth ouAtual(String mes) {
        if (mes == null || mes.isBlank()) {
            return YearMonth.now(BRASILIA);
        }
        try {
            return YearMonth.parse(mes.trim());
        } catch (DateTimeParseException invalido) {
            throw new BusinessException("Mes invalido. Use o formato AAAA-MM.", HttpStatus.BAD_REQUEST, "PEDIDO_INVALIDO");
        }
    }
}
