package com.piggu.common.security;

import java.util.UUID;

/**
 * Usuario autenticado, montado a partir das claims do JWT.
 *
 * @param id    identificador no servico de identidade
 * @param email e-mail normalizado em minusculas; e' a chave usada nas tabelas de dominio
 * @param role  perfil de acesso
 */
public record CurrentUser(UUID id, String email, PigguRole role) {

    public boolean isAdmin() {
        return role == PigguRole.ADMIN;
    }

    /** Dono do registro ou administrador — a mesma checagem que o Apps Script fazia em lugares e notas. */
    public boolean podeGerenciar(String emailDono) {
        return isAdmin() || (emailDono != null && emailDono.equalsIgnoreCase(email));
    }
}
