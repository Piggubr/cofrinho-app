package com.piggu.common.security;

import com.piggu.common.error.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Usuario autenticado, montado a partir das claims do JWT.
 *
 * @param id    identificador no servico de identidade
 * @param email e-mail normalizado em minusculas; e' a chave usada nas tabelas de dominio
 * @param role  perfil de acesso
 * @param plano plano vigente quando o token foi emitido; GRATUITO quando o token nao diz
 */
public record CurrentUser(UUID id, String email, PigguRole role, Plano plano) {

    public CurrentUser {
        plano = plano == null ? Plano.GRATUITO : plano;
    }

    /** Sem plano informado: gratuito. */
    public CurrentUser(UUID id, String email, PigguRole role) {
        this(id, email, role, Plano.GRATUITO);
    }

    public boolean isAdmin() {
        return role == PigguRole.ADMIN;
    }

    /** O ADMIN opera a instalacao e usa tudo; os demais dependem do plano. */
    public boolean isPremium() {
        return isAdmin() || plano == Plano.PREMIUM;
    }

    /** Barra a acao no gratuito com o codigo que o front usa para oferecer o Premium. */
    public void exigirPremium(String recurso) {
        if (!isPremium()) {
            throw new BusinessException(recurso + " faz parte do Piggu Premium.",
                    HttpStatus.UNPROCESSABLE_ENTITY, Plano.CODIGO_PREMIUM);
        }
    }

    /** Dono do registro ou administrador — a mesma checagem que o Apps Script fazia em lugares e notas. */
    public boolean podeGerenciar(String emailDono) {
        return isAdmin() || (emailDono != null && emailDono.equalsIgnoreCase(email));
    }
}
