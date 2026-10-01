package com.piggu.common.web;

import com.piggu.common.error.BusinessException;

import java.util.Currency;
import java.util.Locale;

/** Codigos de moeda ISO 4217, conferidos contra a lista que a propria JVM conhece. */
public final class Moedas {

    private Moedas() {
    }

    /**
     * @return o codigo em maiusculas, se for uma moeda ISO 4217 existente
     * @throws BusinessException para texto que nao e um codigo de moeda
     */
    public static String validar(String codigo) {
        String limpo = codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
        if (!limpo.matches("[A-Z]{3}")) {
            throw new BusinessException("Moeda invalida: use o codigo de tres letras, como EUR ou BRL.");
        }
        try {
            return Currency.getInstance(limpo).getCurrencyCode();
        } catch (IllegalArgumentException erro) {
            throw new BusinessException("Moeda desconhecida: " + limpo + ".");
        }
    }
}
