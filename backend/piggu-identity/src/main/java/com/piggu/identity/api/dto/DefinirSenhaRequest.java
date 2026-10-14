package com.piggu.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Criar ou trocar a senha pelo Perfil.
 *
 * @param senhaAtual so quando a conta ja tem senha
 */
public record DefinirSenhaRequest(@Size(max = 200) String senhaAtual,
                                  @NotBlank(message = "Informe a nova senha.") @Size(max = 200, message = "Senha longa demais.") String novaSenha) {
}
