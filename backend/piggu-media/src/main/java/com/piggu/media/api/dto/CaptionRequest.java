package com.piggu.media.api.dto;

import jakarta.validation.constraints.Size;

public record CaptionRequest(
        @Size(max = 300, message = "A legenda é longa demais.") String legenda
) {
}
