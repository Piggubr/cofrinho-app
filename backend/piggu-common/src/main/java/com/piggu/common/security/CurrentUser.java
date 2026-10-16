package com.piggu.common.security;

import com.piggu.common.error.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

/**
 * Usuario autenticado, montado a partir das claims do JWT.
 *
 * @param id      identificador no servico de identidade
 * @param email   e-mail normalizado em minusculas; so o identity guarda, os outros servicos
 *                marcam o autor pelo {@code id}
 * @param role    papel de acesso
 * @param plano   plano da familia quando o token foi emitido; GRATUITO quando o token nao diz
 * @param familia familia (household) da pessoa; todo dado de dominio pertence a uma
 * @param menores so no token do titular: quem da familia tem 13 a 17 anos, cujos dados
 *                ele tambem ve (ver {@link DonoAtual})
 */
public record CurrentUser(UUID id, String email, PigguRole role, Plano plano, UUID familia, List<UUID> menores) {

    public CurrentUser {
        plano = plano == null ? Plano.GRATUITO : plano;
        menores = menores == null ? List.of() : List.copyOf(menores);
    }

    public CurrentUser(UUID id, String email, PigguRole role, Plano plano, UUID familia) {
        this(id, email, role, plano, familia, List.of());
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

    /** De quem sao os dados que a pessoa ve: os dela e, para o titular, os dos menores. */
    public List<UUID> visiveis() {
        if (menores.isEmpty()) {
            return List.of(id);
        }
        List<UUID> todos = new java.util.ArrayList<>(menores);
        todos.addFirst(id);
        return List.copyOf(todos);
    }

    /** O ADMIN opera a instalacao e usa tudo; os demais dependem do plano da familia. */
    public boolean isPremium() {
        return isAdmin() || plano == Plano.PREMIUM;
    }

    /** Barra a acao no gratuito com o codigo que o front usa para oferecer o Premium. */
    public void exigirPremium(String recurso) {
        if (!isPremium()) {
            throw new BusinessException(recurso + " faz parte do Piggu Premium.",
                    HttpStatus.UNPROCESSABLE_CONTENT, Plano.CODIGO_PREMIUM);
        }
    }

    /**
     * Mexe no registro quem o lancou, e o titular no de um menor (ou no que ficou sem dono, de
     * antes de cada um ter os seus). Registro de outra familia nem chega aqui: o filtro por
     * familia ({@link FamiliaAtual}) ja o deixou fora da consulta.
     */
    public boolean podeGerenciar(UUID dono) {
        return dono == null ? isTitular() : visiveis().contains(dono);
    }

}
