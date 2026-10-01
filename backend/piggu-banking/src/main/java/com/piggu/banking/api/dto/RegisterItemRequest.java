package com.piggu.banking.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Id do item que o widget Pluggy Connect devolveu ao conectar o banco. */
public record RegisterItemRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{1,100}", message = "Id de conexao invalido.") String itemId
) {
}
