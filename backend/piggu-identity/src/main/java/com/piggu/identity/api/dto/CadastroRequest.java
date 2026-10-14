package com.piggu.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cadastro por e-mail e senha.
 *
 * @param pais            onde mora (ISO 3166, duas letras); nulo conta como Brasil
 * @param documento       opcional: CPF no Brasil, o documento de la nos paises que pedem um
 * @param telefone        celular; sem "+" e no Brasil vale DDD + numero
 * @param versaoDosTermos versao dos termos e do aviso que a pessoa aceitou ao se cadastrar
 */
public record CadastroRequest(@NotBlank(message = "Conte como podemos te chamar.") @Size(max = 120, message = "Nome longo demais.") String nome,
                              @NotBlank(message = "Informe seu e-mail.") @Email(message = "Confira o e-mail.") @Size(max = 320) String email,
                              @NotBlank(message = "Crie uma senha.") @Size(max = 200, message = "Senha longa demais.") String senha,
                              @Size(max = 2) String pais,
                              @Size(max = 40) String documento,
                              @NotBlank(message = "Informe seu celular.") @Size(max = 30) String telefone,
                              @Size(max = 20) String versaoDosTermos) {
}
