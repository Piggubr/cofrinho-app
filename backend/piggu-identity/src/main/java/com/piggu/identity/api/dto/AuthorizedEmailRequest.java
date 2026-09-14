package com.piggu.identity.api.dto;

import com.piggu.common.security.PigguRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AuthorizedEmailRequest(
        @NotBlank @Email(message = "Digite um e-mail valido.") String email,
        @NotNull(message = "Escolha o perfil de acesso.") PigguRole role
) {
}
