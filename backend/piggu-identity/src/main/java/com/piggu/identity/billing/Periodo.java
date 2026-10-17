package com.piggu.identity.billing;

/** Plano e periodo de cobranca do Premium. */
public enum Periodo {
    MENSAL,
    ANUAL,
    FAMILIA_MENSAL,
    FAMILIA_ANUAL;

    /** Plano familia: Premium para a casa inteira, ate {@link #PESSOAS_NO_FAMILIA} pessoas. */
    public static final int PESSOAS_NO_FAMILIA = 5;

    public boolean familia() {
        return this == FAMILIA_MENSAL || this == FAMILIA_ANUAL;
    }

    public boolean anual() {
        return this == ANUAL || this == FAMILIA_ANUAL;
    }
}
