package com.piggu.common.security;

import com.piggu.common.error.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Usuario autenticado, montado a partir das claims do JWT.
 *
 * @param id      identificador no servico de identidade
 * @param email   e-mail normalizado em minusculas; marca o autor nas tabelas de dominio
 * @param role    papel de acesso
 * @param plano   plano da familia quando o token foi emitido; GRATUITO quando o token nao diz
 * @param familia familia (household) da pessoa; todo dado de dominio pertence a uma
 */
public record CurrentUser(UUID id, String email, PigguRole role, Plano plano, UUID familia) {

    public CurrentUser {
        plano = plano == null ? Plano.GRATUITO : plano;
    }

    /** Sem plano nem familia informados: gratuito. */
    public CurrentUser(UUID id, String email, PigguRole role) {
        this(id, email, role, Plano.GRATUITO, null);
    }

    public CurrentUser(UUID id, String email, PigguRole role, Plano plano) {
        this(id, email, role, plano, null);
    }

    public boolean isAdmin() {
        return role == PigguRole.ADMIN;
    }

    /** Titular da familia; o ADMIN vale como titular dentro da propria familia. */
    public boolean isTitular() {
        return role == PigguRole.TITULAR || isAdmin();
    }

    /** O ADMIN opera a instalacao e usa tudo; os demais dependem do plano da familia. */
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

    /**
     * Autor do registro ou titular da familia. Registro de outra familia nem chega aqui:
     * o filtro por familia ({@link FamiliaAtual}) ja o deixou fora da consulta.
     */
    public boolean podeGerenciar(String emailDono) {
        return isTitular() || (emailDono != null && emailDono.equalsIgnoreCase(email));
    }
}
