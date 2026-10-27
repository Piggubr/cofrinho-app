package com.piggu.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Entrada por e-mail e senha; {@code lembrar} e {@code refreshNoCorpo} como na entrada pelo Google. */
public record SenhaLoginRequest(@NotBlank(message = "Informe seu e-mail.") @Size(max = 320) String email,
                                @NotBlank(message = "Informe sua senha.") @Size(max = 200) String senha,
                                Boolean lembrar,
                                Boolean refreshNoCorpo) {
}
