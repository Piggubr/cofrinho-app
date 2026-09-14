package com.piggu.finance.domain;

import java.util.List;

/**
 * As oito categorias que o Piggu sempre teve.
 *
 * <p>Continuam no codigo, e nao no banco, porque participam das regras de validacao:
 * um gasto com categoria desconhecida cai em Outros. As categorias criadas pelo
 * usuario ficam na tabela custom_categories e sao somadas a estas.</p>
 */
public final class Categorias {

    public static final String PADRAO = "Outros";

    public static final List<String> BASE = List.of(
            "Aluguel",
            "Alimentação",
            "Transporte",
            "Farmácia/Saúde",
            "Lazer",
            "Assinaturas",
            "Gastos Extras",
            "Outros"
    );

    private Categorias() {
    }
}
