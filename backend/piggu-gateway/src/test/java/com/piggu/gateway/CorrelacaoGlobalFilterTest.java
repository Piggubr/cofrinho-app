package com.piggu.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelacaoGlobalFilterTest {

    private final CorrelacaoGlobalFilter filtro = new CorrelacaoGlobalFilter();

    /** Roda o filtro e devolve o pedido como chegou ao servico de destino. */
    private ServerWebExchange passar(MockServerWebExchange troca) {
        AtomicReference<ServerWebExchange> repassada = new AtomicReference<>();
        filtro.filter(troca, adiante -> {
            repassada.set(adiante);
            return Mono.empty();
        }).block();
        return repassada.get();
    }

    @Test
    @DisplayName("sem id, gera um e manda o mesmo para o servico e para o front")
    void geraId() {
        MockServerWebExchange troca = MockServerWebExchange.from(MockServerHttpRequest.get("/api/expenses"));

        ServerWebExchange repassada = passar(troca);

        String id = repassada.getRequest().getHeaders().getFirst("X-Request-Id");
        assertThat(id).matches("[0-9a-f-]{36}");
        assertThat(troca.getResponse().getHeaders().getFirst("X-Request-Id")).isEqualTo(id);
    }

    @Test
    @DisplayName("mantem id valido do front e troca id forjado")
    void validaIdRecebido() {
        ServerWebExchange valido = passar(MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/x").header("X-Request-Id", "front-42")));
        ServerWebExchange forjado = passar(MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/x").header("X-Request-Id", "a".repeat(200))));

        assertThat(valido.getRequest().getHeaders().getFirst("X-Request-Id")).isEqualTo("front-42");
        assertThat(forjado.getRequest().getHeaders().getFirst("X-Request-Id")).hasSize(36);
    }
}
