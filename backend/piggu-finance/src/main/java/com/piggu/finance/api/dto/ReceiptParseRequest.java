package com.piggu.finance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Foto de recibo enviada para leitura automatica.
 *
 * @param imageBase64 conteudo da imagem em base64, sem o prefixo data:
 * @param mimeType      tipo da imagem; se nao for reconhecido, assume image/jpeg
 * @param autorizoIa    a pessoa autoriza mandar a foto ao Gemini quando a leitura propria falhar
 * @param versaoDoAviso versao do aviso de privacidade que a pessoa viu ao autorizar
 */
public record ReceiptParseRequest(
        @NotBlank(message = "A foto do recibo nao chegou.") String imageBase64,
        @Size(max = 50) String mimeType,
        Boolean autorizoIa,
        @Size(max = 20) String versaoDoAviso
) {

    public ReceiptParseRequest(String imageBase64, String mimeType) {
        this(imageBase64, mimeType, null, null);
    }
}
