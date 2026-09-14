package com.piggu.common.security;

import java.util.Locale;

/**
 * Perfis de acesso, herdados direto do Apps Script.
 *
 * <ul>
 *   <li>{@link #ADMIN} — administra premios, Fofocoins e pode apagar conteudo alheio.</li>
 *   <li>{@link #BEATRIZ} — usuaria principal; acesso completo ao proprio conteudo.</li>
 *   <li>{@link #FAMILIAR} — convidado; so consulta o painel e deposita no cofrinho.</li>
 * </ul>
 */
public enum PigguRole {

    ADMIN,
    BEATRIZ,
    FAMILIAR;

    public static final String AUTHORITY_PREFIX = "ROLE_";

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }

    /** Converte texto vindo do banco ou do token, caindo em {@link #FAMILIAR} quando nao reconhece. */
    public static PigguRole of(String valor) {
        if (valor == null || valor.isBlank()) {
            return FAMILIAR;
        }
        try {
            return valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException erro) {
            return FAMILIAR;
        }
    }
}
