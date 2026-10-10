package com.piggu.common.dados;

import java.util.Locale;

/**
 * O que uma exclusao alcanca.
 *
 * <p>Vem na claim {@code exclusao} de um token curto que so o identity emite, nunca de
 * um parametro: o token comum de uma pessoa nao carrega essa claim, entao nao apaga
 * nada mesmo que alcance a rota.</p>
 */
public enum EscopoDeExclusao {
    /** A pessoa sai; a familia fica com o que era compartilhado, anonimizado. */
    PESSOA,
    /** A pessoa era a ultima da familia: tudo sai. */
    FAMILIA;

    public static final String CLAIM = "exclusao";

    public static EscopoDeExclusao de(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException desconhecido) {
            return null;
        }
    }
}
