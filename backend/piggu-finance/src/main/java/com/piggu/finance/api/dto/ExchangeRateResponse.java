package com.piggu.finance.api.dto;

import java.math.BigDecimal;

/**
 * Cotacao de referencia EUR para BRL.
 *
 * @param estimativa    sempre verdadeiro: e referencia de bancos centrais, nao inclui taxas
 * @param desatualizada verdadeiro quando a cotacao veio do ultimo valor guardado,
 *                      porque o servico de cambio nao respondeu
 */
public record ExchangeRateResponse(
        BigDecimal taxa,
        String data,
        String fonte,
        boolean estimativa,
        boolean desatualizada
) {
}
