package com.piggu.common.error;

import java.time.Instant;
import java.util.List;

/**
 * Corpo unico de erro devolvido por todos os servicos.
 *
 * @param erro     mensagem pronta para exibir ao usuario
 * @param codigo   identificador estavel para o front tratar casos especificos
 * @param campos   erros de validacao por campo, quando houver
 * @param momento  instante do erro
 */
public record ApiError(String erro, String codigo, List<CampoInvalido> campos, Instant momento) {

    public record CampoInvalido(String campo, String mensagem) {
    }

    public static ApiError de(String mensagem, String codigo) {
        return new ApiError(mensagem, codigo, List.of(), Instant.now());
    }

    public static ApiError validacao(String mensagem, List<CampoInvalido> campos) {
        return new ApiError(mensagem, "VALIDACAO", campos, Instant.now());
    }
}
