package com.piggu.common.security;

import java.util.Locale;

/**
 * Papeis de acesso. Cada pessoa tem conta propria e pertence a uma familia.
 *
 * <ul>
 *   <li>{@link #ADMIN} — opera a instalacao; dentro da propria familia vale como titular.</li>
 *   <li>{@link #TITULAR} — dono da familia: convida membros, assina o Premium e gerencia tudo dela.</li>
 *   <li>{@link #PARCEIRO} — promovido pelo titular; lanca e edita os dados da familia como ele,
 *       mas nao mexe no plano, nos convites nem tira pessoas.</li>
 *   <li>{@link #MEMBRO} — convidado; so consulta o painel e deposita no cofrinho.</li>
 * </ul>
 */
public enum PigguRole {

    ADMIN,
    TITULAR,
    PARCEIRO,
    MEMBRO;

    public static final String AUTHORITY_PREFIX = "ROLE_";

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }

    /**
     * Converte texto vindo do banco ou do token, caindo em {@link #MEMBRO} quando nao reconhece.
     * Os nomes antigos (BEATRIZ, FAMILIAR) ainda chegam em tokens emitidos antes da troca.
     */
    public static PigguRole of(String valor) {
        if (valor == null || valor.isBlank()) {
            return MEMBRO;
        }
        return switch (valor.trim().toUpperCase(Locale.ROOT)) {
            case "ADMIN" -> ADMIN;
            case "TITULAR", "BEATRIZ" -> TITULAR;
            case "PARCEIRO" -> PARCEIRO;
            default -> MEMBRO;
        };
    }
}
