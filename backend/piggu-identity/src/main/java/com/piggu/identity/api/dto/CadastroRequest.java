package com.piggu.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cadastro por e-mail e senha.
 *
 * @param cpf             obrigatorio para quem mora no Brasil
 * @param moraNoBrasil    nulo conta como sim
 * @param versaoDosTermos versao dos termos e do aviso que a pessoa aceitou ao se cadastrar
 * @param lembrar         continuar conectado; falso deixa o cookie so ate fechar o navegador
 */
public record CadastroRequest(@NotBlank(message = "Conte como podemos te chamar.") @Size(max = 120, message = "Nome longo demais.") String nome,
                              @NotBlank(message = "Informe seu e-mail.") @Email(message = "Confira o e-mail.") @Size(max = 320) String email,
                              @NotBlank(message = "Crie uma senha.") @Size(max = 200, message = "Senha longa demais.") String senha,
                              @Size(max = 20) String cpf,
                              Boolean moraNoBrasil,
                              @Size(max = 20) String versaoDosTermos,
                              Boolean lembrar) {
}
