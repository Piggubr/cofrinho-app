package com.piggu.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param idToken credencial devolvida pelo Google Sign-In no navegador
 */
public record GoogleLoginRequest(@NotBlank(message = "Faca login com Google para continuar.") String idToken) {
}
