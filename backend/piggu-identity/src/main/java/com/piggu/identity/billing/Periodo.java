package com.piggu.identity.billing;

/** Plano e periodo de cobranca do Premium. */
public enum Periodo {
    MENSAL,
    ANUAL,
    FAMILIA_MENSAL,
    FAMILIA_ANUAL,
    CASAL_MENSAL,
    CASAL_ANUAL;

    /** Plano familia: Premium para a casa inteira, ate {@link #PESSOAS_NO_FAMILIA} pessoas. */
    public static final int PESSOAS_NO_FAMILIA = 5;

    /** Plano casal: Premium para o titular e o parceiro. */
    public static final int PESSOAS_NO_CASAL = 2;

    public boolean familia() {
        return this == FAMILIA_MENSAL || this == FAMILIA_ANUAL;
    }

    public boolean casal() {
        return this == CASAL_MENSAL || this == CASAL_ANUAL;
    }

    public TipoDePlano tipo() {
        return familia() ? TipoDePlano.FAMILIA : casal() ? TipoDePlano.CASAL : TipoDePlano.INDIVIDUAL;
    }

    public boolean anual() {
        return this == ANUAL || this == FAMILIA_ANUAL || this == CASAL_ANUAL;
    }
}
