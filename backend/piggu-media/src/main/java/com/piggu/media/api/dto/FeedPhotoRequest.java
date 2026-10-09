package com.piggu.media.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FeedPhotoRequest(
        @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "Mes invalido.")
        String mesKey,

        @NotBlank(message = "A foto nao chegou.")
        @Size(max = 7_000_000, message = "A foto e grande demais. O limite e de 5 MB.")
        String imageBase64,

        @Pattern(regexp = "image/[a-z0-9.+-]{1,30}", message = "Formato de imagem nao aceito.")
        String mimeType
) {
}
