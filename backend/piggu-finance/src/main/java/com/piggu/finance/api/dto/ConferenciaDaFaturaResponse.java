package com.piggu.finance.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Resultado da conferencia da fatura.
 *
 * @param origem PROPRIA (leitor por regras) ou GEMINI
 * @param aviso  leitura parcial ou que nao fechou; nulo quando fechou
 * @param linhas so no Premium; nulo no gratuito (o servidor nao devolve as linhas)
 */
public record ConferenciaDaFaturaResponse(String banco, LocalDate vencimento, BigDecimal total, BigDecimal somaLida,
                                          String origem, String aviso, Contagem contagem, List<Linha> linhas) {

    public record Contagem(int compras, int tarifas, int ignoradas, int jaLancadas, int confira, int faltam) {
    }

    /**
     * @param tipo     COMPRA ou TARIFA (as IGNORADAS so contam)
     * @param situacao JA_LANCADA, CONFIRA ou FALTA
     * @param gastoId  o gasto ja lancado (JA_LANCADA) ou o parecido (CONFIRA); nulo em FALTA
     */
    public record Linha(LocalDate data, String descricao, BigDecimal valor, String tipo, Integer parcelaNumero,
                        Integer parcelaTotal, String situacao, UUID gastoId, String gastoDescricao,
                        LocalDate gastoData, String categoria, String idExterno) {
    }
}
