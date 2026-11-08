package com.piggu.finance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * PDF da fatura do cartao para conferir com os gastos ja lancados. Nada e gravado.
 *
 * @param pdfBase64     o PDF em base64 (ate 5 MB depois de decodificado)
 * @param senha         senha do PDF, quando o banco protege o arquivo
 * @param contaId       cartao da fatura; opcional. Com ele, o periodo e o da fatura e o cartao desempata
 * @param autorizoIa    a pessoa autoriza mandar o texto da fatura ao Gemini quando a leitura propria falhar
 * @param versaoDoAviso versao do aviso de privacidade que a pessoa viu ao autorizar
 */
public record ConferirFaturaRequest(
        @NotBlank(message = "O PDF da fatura nao chegou.")
        @Size(max = 7_000_000, message = "PDF grande demais (máximo 5 MB).") String pdfBase64,
        @Size(max = 100) String senha,
        UUID contaId,
        Boolean autorizoIa,
        @Size(max = 20) String versaoDoAviso
) {
}
