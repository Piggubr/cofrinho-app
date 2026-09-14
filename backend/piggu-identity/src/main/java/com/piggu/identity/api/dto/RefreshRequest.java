package com.piggu.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank(message = "Sessao ausente.") String refreshToken) {
}
