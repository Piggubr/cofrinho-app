package com.piggu.finance.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Resultado da leitura de um recibo. Nada e gravado ainda: o usuario confere os
 * itens na tela e so entao envia para POST /api/expenses.
 *
 * @param origem OCR (leitor proprio, a foto nao sai do servidor) ou GEMINI
 * @param aviso  o que conferir com mais cuidado; nulo quando a leitura fechou
 */
public record ReceiptParseResponse(
        UUID reciboId,
        String estabelecimento,
        LocalDate data,
        List<Item> itens,
        String origem,
        String aviso
) {

    public record Item(String item, String categoria, BigDecimal valor) {
    }
}
