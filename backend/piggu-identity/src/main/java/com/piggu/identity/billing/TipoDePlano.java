package com.piggu.identity.billing;

/**
 * Quem da casa fica Premium com a assinatura.
 *
 * <ul>
 *   <li>{@link #INDIVIDUAL} — so quem assinou.</li>
 *   <li>{@link #CASAL} — o casal: titular e parceiro (Eduardo, 2026-10-22).</li>
 *   <li>{@link #FAMILIA} — a casa inteira, ate {@link Periodo#PESSOAS_NO_FAMILIA} pessoas.</li>
 * </ul>
 */
public enum TipoDePlano {
    INDIVIDUAL,
    CASAL,
    FAMILIA
}
