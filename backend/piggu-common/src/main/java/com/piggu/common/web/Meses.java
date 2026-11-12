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

    /** AAAA-MM com mes de 01 a 12: o mes com dois digitos quaisquer de antes deixava passar 2026-13 (BUG-007). */
    public static final String FORMATO = "^\\d{4}-(0[1-9]|1[0-2])$";

    /** Mensagem de mes invalido, igual no corpo e na URL. */
    public static final String INVALIDO = "Mês inválido. Use o formato AAAA-MM.";

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
            throw new BusinessException(INVALIDO, HttpStatus.BAD_REQUEST, "PEDIDO_INVALIDO");
        }
    }
}
