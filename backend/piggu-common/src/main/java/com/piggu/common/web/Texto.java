package com.piggu.common.web;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Higienizacao de texto vinda do Apps Script.
 *
 * <p>O codigo original repetia {@code String(valor || '').trim().slice(0, N)} em quase toda
 * funcao de escrita. Aqui isso vira um lugar so, com o mesmo comportamento: nulo virou
 * string vazia, espacos das pontas somem e o texto e' cortado no limite da coluna.</p>
 */
public final class Texto {

    private static final java.util.regex.Pattern ESPACOS = java.util.regex.Pattern.compile("\\s+");
    private static final java.util.regex.Pattern DIACRITICOS =
            java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private Texto() {
    }

    /** Aparado e cortado em {@code limite} caracteres. Nunca devolve {@code null}. */
    public static String limitar(String valor, int limite) {
        if (valor == null) {
            return "";
        }
        String limpo = valor.trim();
        return limpo.length() <= limite ? limpo : limpo.substring(0, limite);
    }

    /** Igual a {@link #limitar}, mas devolve {@code padrao} quando o resultado fica vazio. */
    public static String limitarOuPadrao(String valor, int limite, String padrao) {
        String limpo = limitar(valor, limite);
        return limpo.isEmpty() ? padrao : limpo;
    }

    /** E-mail em minusculas e sem espacos — a forma usada como chave em todas as tabelas. */
    public static String email(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }

    /** Colapsa espacos repetidos em um unico espaco. */
    public static String espacoUnico(String valor) {
        return valor == null ? "" : ESPACOS.matcher(valor.trim()).replaceAll(" ");
    }

    /** Remove acentos, mantendo as letras base. */
    public static String semAcento(String valor) {
        if (valor == null) {
            return "";
        }
        return DIACRITICOS.matcher(Normalizer.normalize(valor, Normalizer.Form.NFD)).replaceAll("");
    }

    public static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
