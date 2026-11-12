package com.piggu.common.web;

/**
 * Minimo e teto dos campos de valor, iguais no backend e nos apps (D-189). Constantes para
 * caber nas anotacoes de validacao ({@code @DecimalMin(value = Valores.MINIMO, ...)}).
 */
public final class Valores {

    /** Nenhum valor de dinheiro e zero: um centavo e o menor (D-189). */
    public static final String MINIMO = "0.01";
    /** Gasto, orcamento, meta e limite. */
    public static final String MAXIMO = "1000000";
    /** Receita, valor a receber e deposito no cofrinho. */
    public static final String MAXIMO_DE_ENTRADA = "10000000";

    public static final String ABAIXO_DO_MINIMO = "Digite um valor a partir de R$ 0,01.";
    public static final String ACIMA_DO_MAXIMO = "O valor máximo é R$ 1.000.000,00.";
    public static final String ACIMA_DO_MAXIMO_DE_ENTRADA = "O valor máximo é R$ 10.000.000,00.";

    private Valores() {
    }
}
