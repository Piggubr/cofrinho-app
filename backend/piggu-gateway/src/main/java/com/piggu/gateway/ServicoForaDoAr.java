package com.piggu.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Servico fora do ar vira 503, e servico que nao responde a tempo vira 504, sempre com o
 * corpo de erro de sempre (ApiError) e o codigo {@code SERVICO_INDISPONIVEL} (D-191).
 *
 * <p>Antes, o erro de conexao caia no tratador padrao do Spring e saia 500 com outro corpo:
 * o app nao distinguia "fora do ar" de "erro interno". O resto segue para o tratador padrao.</p>
 */
@Component
@Order(-2)
public class ServicoForaDoAr implements WebExceptionHandler {

    static final String MENSAGEM = "Essa parte do Piggu está fora do ar agora. Tente de novo em instantes.";
    private static final Logger log = LoggerFactory.getLogger(ServicoForaDoAr.class);

    @Override
    public Mono<Void> handle(ServerWebExchange troca, Throwable erro) {
        HttpStatus status = statusPara(erro);
        if (status == null || troca.getResponse().isCommitted()) {
            return Mono.error(erro);
        }
        log.warn("Servico sem resposta ({}): {}", status.value(), erro.toString());
        return escrever(troca.getResponse(), status, "SERVICO_INDISPONIVEL", MENSAGEM);
    }

    /** 504 para tempo esgotado, 503 para conexao que nao abre; nulo para o resto. */
    static HttpStatus statusPara(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof ResponseStatusException comStatus
                    && comStatus.getStatusCode().value() == HttpStatus.GATEWAY_TIMEOUT.value()
                    || causa instanceof java.util.concurrent.TimeoutException
                    || causa instanceof io.netty.channel.ConnectTimeoutException) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
        }
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConnectException || causa instanceof UnknownHostException
                    || causa instanceof reactor.netty.http.client.PrematureCloseException) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            }
        }
        return null;
    }

    /** Mesmo corpo de erro dos servicos (ApiError), que os apps ja sabem mostrar. */
    static Mono<Void> escrever(ServerHttpResponse resposta, HttpStatus status, String codigo, String mensagem) {
        resposta.setStatusCode(status);
        resposta.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String corpo = "{\"erro\":\"" + mensagem + "\",\"codigo\":\"" + codigo
                + "\",\"campos\":[],\"momento\":\"" + Instant.now() + "\"}";
        DataBuffer buffer = resposta.bufferFactory().wrap(corpo.getBytes(StandardCharsets.UTF_8));
        return resposta.writeWith(Mono.just(buffer));
    }
}
