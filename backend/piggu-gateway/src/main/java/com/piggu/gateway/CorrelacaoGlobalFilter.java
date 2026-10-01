package com.piggu.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Da a cada chamada um {@code X-Request-Id}, repassa aos servicos e devolve ao front.
 *
 * <p>O id que chega de fora so e mantido se for curto e sem caracteres estranhos:
 * aceitar qualquer texto deixaria alguem escrever linhas falsas no log. O log de acesso
 * traz o id da rota, nao o caminho, porque caminho pode carregar e-mail.</p>
 */
@Component
public class CorrelacaoGlobalFilter implements GlobalFilter, Ordered {

    public static final String CABECALHO = "X-Request-Id";
    private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final Logger log = LoggerFactory.getLogger("com.piggu.acesso");

    @Override
    public Mono<Void> filter(ServerWebExchange troca, GatewayFilterChain cadeia) {
        String recebido = troca.getRequest().getHeaders().getFirst(CABECALHO);
        String id = recebido != null && VALIDO.matcher(recebido).matches() ? recebido : UUID.randomUUID().toString();

        ServerHttpRequest pedido = troca.getRequest().mutate().headers(h -> h.set(CABECALHO, id)).build();
        troca.getResponse().getHeaders().set(CABECALHO, id);
        long inicio = System.nanoTime();

        return cadeia.filter(troca.mutate().request(pedido).build())
                .doFinally(sinal -> {
                    Route rota = troca.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                    log.info("req={} {} rota={} -> {} em {} ms", id, pedido.getMethod(),
                            rota == null ? "-" : rota.getId(), troca.getResponse().getStatusCode(),
                            (System.nanoTime() - inicio) / 1_000_000);
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
