package com.piggu.identity.domain;

import com.piggu.common.error.BusinessException;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Telas que nao sao de dinheiro e cada pessoa liga ou desliga no perfil, para o menu
 * ficar so com o que ela usa. Desligar so tira do menu: nada e apagado.
 */
public final class ModulosDeEstiloDeVida {

    public static final List<String> TODOS = List.of("compras", "lugares", "filmes", "fotos", "premios");

    private ModulosDeEstiloDeVida() {
    }

    /** Coluna para lista: NULL (conta antiga) liga todos; vazio, nenhum. */
    public static List<String> ler(String coluna) {
        if (coluna == null) {
            return TODOS;
        }
        return Arrays.stream(coluna.split(",")).filter(TODOS::contains).toList();
    }

    /** Lista para coluna, na ordem do menu e sem repetir; modulo desconhecido e recusado. */
    public static String gravar(List<String> escolhidos) {
        List<String> normalizados = escolhidos.stream().map(m -> m.trim().toLowerCase(Locale.ROOT)).toList();
        normalizados.stream().filter(m -> !TODOS.contains(m)).findFirst().ifPresent(desconhecido -> {
            throw new BusinessException("Modulo desconhecido: " + desconhecido);
        });
        return String.join(",", TODOS.stream().filter(normalizados::contains).toList());
    }
}
