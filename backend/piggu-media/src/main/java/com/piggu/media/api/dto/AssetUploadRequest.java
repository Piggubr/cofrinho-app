package com.piggu.media.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Envio de uma imagem.
 *
 * @param imageBase64 bytes em base64, sem o prefixo data:
 * @param mimeType    tipo da imagem; so jpeg, png e webp sao aceitos
 * @param contexto    rotulo de quem e a imagem, como FEED ou LUGAR
 */
public record AssetUploadRequest(
        @NotBlank(message = "A foto nao chegou.") @Size(max = 7_000_000, message = "A foto e grande demais. O limite e de 5 MB.") String imageBase64,
        @Pattern(regexp = "image/[a-z0-9.+-]{1,30}", message = "Formato de imagem nao aceito.") String mimeType,
        @Size(max = 30) String contexto
) {
}
