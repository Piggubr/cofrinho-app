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
    private final String codigo;

    public BusinessException(String message) {
        this(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public BusinessException(String message, HttpStatus status) {
        this(message, status, null);
    }

    /** @param codigo identificador estavel para o front; nulo usa o nome da classe */
    public BusinessException(String message, HttpStatus status, String codigo) {
        super(message);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo == null ? getClass().getSimpleName() : codigo;
    }
}
