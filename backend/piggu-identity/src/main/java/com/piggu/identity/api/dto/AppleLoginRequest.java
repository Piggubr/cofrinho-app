package com.piggu.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Entrar com Apple pelo app nativo.
 *
 * @param idToken         identity token que o AuthenticationServices devolve
 * @param nonce           o nonce original, sem hash: o app manda o SHA-256 dele para a Apple
 *                        e o servidor confere que o token carrega esse hash
 * @param nome            nome que a Apple entrega so na primeira autorizacao; usado ao criar a conta
 * @param versaoDosTermos como no Google: so conta nova precisa
 * @param lembrar         como no Google
 * @param refreshNoCorpo  como no Google; o app nativo sempre manda true
 */
public record AppleLoginRequest(@NotBlank(message = "Faca login com a Apple para continuar.") @Size(max = 4096) String idToken,
                                @NotBlank(message = "Faca login com a Apple para continuar.") @Size(max = 200) String nonce,
                                @Size(max = 120) String nome,
                                @Size(max = 20) String versaoDosTermos,
                                Boolean lembrar,
                                Boolean refreshNoCorpo) {
}
