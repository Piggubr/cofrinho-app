package com.piggu.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Com o Open Finance desligado ({@code PIGGU_OPEN_FINANCE=false}), o banking nem sobe e o
 * Painel perguntava o status a cada abertura, gerando um erro por vez (BUG-005). A resposta
 * agora sai daqui, sem chamar o banking (D-191).
 */
@Component
public class BankingDesligadoGlobalFilter implements GlobalFilter, Ordered {

    private final boolean ligado;

    public BankingDesligadoGlobalFilter(@Value("${piggu.open-finance.ligado:false}") boolean ligado) {
        this.ligado = ligado;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange troca, GatewayFilterChain cadeia) {
        if (ligado || troca.getRequest().getMethod() != HttpMethod.GET
                || !"/api/banking/status".equals(troca.getRequest().getPath().value())) {
            return cadeia.filter(troca);
        }
        ServerHttpResponse resposta = troca.getResponse();
        resposta.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer corpo = resposta.bufferFactory().wrap("{\"habilitado\":false}".getBytes(StandardCharsets.UTF_8));
        return resposta.writeWith(Mono.just(corpo));
    }

    /** Depois da correlacao e do limite por IP: a resposta leva X-Request-Id e conta no teto. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
