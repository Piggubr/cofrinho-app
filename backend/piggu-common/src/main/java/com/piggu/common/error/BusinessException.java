package com.piggu.common.error;

import org.springframework.http.HttpStatus;

/**
 * Erro de regra de negocio cuja mensagem pode ser exibida ao usuario.
 *
 * <p>No Apps Script todo erro virava {@code throw new Error(texto)} e a funcao
 * {@code mensagemSegura_} devolvia esse texto direto para a tela. Aqui a mesma ideia
 * fica explicita: o que estende esta classe e' mostravel; qualquer outra excecao vira
 * uma mensagem generica.</p>
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message) {
        this(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
