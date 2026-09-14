package com.piggu.media.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record FeedPhotoRequest(
        @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "Mes invalido.")
        String mesKey,

        @NotBlank(message = "A foto nao chegou.")
        String imageBase64,

        String mimeType
) {
}
