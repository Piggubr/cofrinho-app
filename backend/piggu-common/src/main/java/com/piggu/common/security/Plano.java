package com.piggu.common.security;

import java.util.Locale;

/**
 * Plano da conta.
 *
 * <p>O identity grava o plano vigente na claim {@code plano}, e cada servico le dali
 * para barrar o que e Premium sem perguntar ao identity. Ausente ou desconhecido vale
 * GRATUITO: um token antigo nunca ganha o que nao pagou.</p>
 *
 * <p>Regra do produto: o Premium so barra <em>criar</em> ou <em>buscar</em> algo novo.
 * Ver, exportar, apagar e desconectar o que ja existe seguem livres no gratuito, para
 * que um Premium vencido nunca prenda dado da pessoa.</p>
 */
public enum Plano {
    GRATUITO,
    PREMIUM;

    /** Codigo de erro de quando o gratuito tenta usar algo do Premium. */
    public static final String CODIGO_PREMIUM = "PLANO_PREMIUM";

    public static Plano de(String valor) {
        if (valor == null) {
            return GRATUITO;
        }
        try {
            return valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException desconhecido) {
            return GRATUITO;
        }
    }
}
