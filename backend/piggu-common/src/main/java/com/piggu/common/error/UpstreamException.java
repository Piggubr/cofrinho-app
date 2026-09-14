package com.piggu.common.error;

import org.springframework.http.HttpStatus;

/**
 * Falha ao conversar com um servico externo (Gemini, TMDB, OpenFoodFacts, cambio).
 * A mensagem e' escrita para o usuario final, sem vazar detalhe tecnico.
 */
public class UpstreamException extends BusinessException {

    public UpstreamException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }
}
