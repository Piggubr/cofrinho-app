package com.piggu.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param idToken         credencial devolvida pelo Google Sign-In no navegador
 * @param versaoDosTermos versao dos termos e do aviso que a pessoa aceitou; so conta
 *                        nova precisa, e o backend pede quando falta
 */
public record GoogleLoginRequest(@NotBlank(message = "Faca login com Google para continuar.") String idToken,
                                 @Size(max = 20) String versaoDosTermos) {
}
