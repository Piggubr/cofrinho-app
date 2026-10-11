package com.piggu.identity.avisos;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Um aviso como o financeiro manda em GET /api/alerts. */
public record Aviso(String chave, String tipo, String assunto, BigDecimal valor, BigDecimal limite,
                    BigDecimal percentual, LocalDate data) {
}
