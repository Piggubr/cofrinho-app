package com.piggu.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Traduz excecoes em respostas HTTP consistentes.
 *
 * <p>Regra: {@link BusinessException} carrega texto escrito para o usuario e vai inteiro
 * para a resposta. Qualquer outra excecao vira 500 com mensagem generica, e o detalhe
 * real fica apenas no log do servidor.</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> tratarNegocio(BusinessException erro) {
        return ResponseEntity.status(erro.getStatus())
                .body(ApiError.de(erro.getMessage(), erro.getCodigo()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> tratarValidacao(MethodArgumentNotValidException erro) {
        List<ApiError.CampoInvalido> campos = erro.getBindingResult().getFieldErrors().stream()
                .map(campo -> new ApiError.CampoInvalido(campo.getField(), mensagemDoCampo(campo)))
                .toList();
        String resumo = campos.isEmpty() ? "Confira os dados enviados." : campos.get(0).mensagem();
        return ResponseEntity.badRequest().body(ApiError.validacao(resumo, campos));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> tratarPedidoMalFormado(Exception erro) {
        log.debug("Pedido mal formado", erro);
        return ResponseEntity.badRequest()
                .body(ApiError.de("Nao entendi os dados enviados.", "PEDIDO_INVALIDO"));
    }

    /**
     * Erros de protocolo do proprio Spring MVC: metodo nao suportado, endereco
     * inexistente, tipo de conteudo recusado.
     *
     * <p>Sem este bloco eles cairiam no catch-all abaixo e um 405 honesto viraria
     * um 500, escondendo do front que o problema esta na chamada, nao no servidor.</p>
     */
    @ExceptionHandler({
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class,
            NoHandlerFoundException.class,
            NoResourceFoundException.class,
            ErrorResponseException.class
    })
    public ResponseEntity<ApiError> tratarProtocolo(Exception erro) {
        HttpStatusCode status = erro instanceof ErrorResponse resposta
                ? resposta.getStatusCode()
                : HttpStatus.BAD_REQUEST;
        log.debug("Chamada fora do contrato: {}", erro.getMessage());
        return ResponseEntity.status(status).body(ApiError.de(mensagemDoProtocolo(status), "PEDIDO_INVALIDO"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> tratarAcessoNegado(AccessDeniedException erro) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.de("Esta acao nao esta disponivel para o seu perfil.", "SEM_PERMISSAO"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> tratarInesperado(Exception erro, HttpServletRequest pedido) {
        log.error("Falha inesperada em {} {}", pedido.getMethod(), pedido.getRequestURI(), erro);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.de("Ocorreu um erro. Tente novamente.", "ERRO_INTERNO"));
    }

    private String mensagemDoProtocolo(HttpStatusCode status) {
        if (status.isSameCodeAs(HttpStatus.METHOD_NOT_ALLOWED)) {
            return "Esta operacao nao existe neste endereco.";
        }
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return "Endereco nao encontrado.";
        }
        if (status.isSameCodeAs(HttpStatus.UNSUPPORTED_MEDIA_TYPE)) {
            return "Formato de conteudo nao suportado.";
        }
        return "Nao entendi os dados enviados.";
    }

    private String mensagemDoCampo(FieldError campo) {
        return campo.getDefaultMessage() == null ? "Valor invalido." : campo.getDefaultMessage();
    }
}
