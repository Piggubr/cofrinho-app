package com.piggu.finance.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Foto de recibo enviada para leitura automatica.
 *
 * @param imageBase64 conteudo da imagem em base64, sem o prefixo data:
 * @param mimeType    tipo da imagem; se nao for reconhecido, assume image/jpeg
 */
public record ReceiptParseRequest(
        @NotBlank(message = "A foto do recibo nao chegou.") String imageBase64,
        String mimeType
) {
}
