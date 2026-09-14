package com.piggu.lifestyle.domain;

import java.util.List;

/**
 * Marcadores de fabrica dos lugares.
 *
 * <p>Continuam no codigo porque participam da validacao: um marcador desconhecido
 * e descartado em vez de gravado, como no Apps Script.</p>
 */
public final class MarcadoresLugar {

    public static final List<String> BASE = List.of(
            "Favorito",
            "Melhor custo-beneficio",
            "Voltaria",
            "Nao voltaria"
    );

    private MarcadoresLugar() {
    }
}
