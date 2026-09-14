package com.piggu.media.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Envio de uma imagem.
 *
 * @param imageBase64 bytes em base64, sem o prefixo data:
 * @param mimeType    tipo da imagem; so jpeg, png e webp sao aceitos
 * @param contexto    rotulo de quem e a imagem, como FEED ou LUGAR
 */
public record AssetUploadRequest(
        @NotBlank(message = "A foto nao chegou.") String imageBase64,
        String mimeType,
        String contexto
) {
}
