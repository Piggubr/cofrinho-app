package com.piggu.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LimitePorIpGlobalFilterTest {

    private final RelogioDeTeste relogio = new RelogioDeTeste();
    private final AtomicInteger repassadas = new AtomicInteger();

    private MockServerWebExchange chamar(LimitePorIpGlobalFilter filtro, MockServerHttpRequest.BaseBuilder<?> pedido) {
        MockServerWebExchange troca = MockServerWebExchange.from(pedido);
        filtro.filter(troca, adiante -> {
            repassadas.incrementAndGet();
            return Mono.empty();
        }).block();
        return troca;
    }

    private static MockServerHttpRequest.BaseBuilder<?> doIp(String ip) {
        return MockServerHttpRequest.get("/api/expenses").header("X-Forwarded-For", ip);
    }

    @Test
    @DisplayName("passa do teto: 429 com Retry-After e o corpo de erro de sempre; outro IP segue")
    void recusaAcimaDoTeto() {
        LimitePorIpGlobalFilter filtro = new LimitePorIpGlobalFilter(3, relogio);

        for (int i = 0; i < 3; i++) {
            assertThat(chamar(filtro, doIp("200.1.1.1")).getResponse().getStatusCode()).isNull();
        }
        MockServerWebExchange recusada = chamar(filtro, doIp("200.1.1.1"));
        MockServerWebExchange outroIp = chamar(filtro, doIp("200.2.2.2"));

        assertThat(recusada.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(recusada.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(recusada.getResponse().getBodyAsString().block()).contains("\"codigo\":\"LIMITE_DE_CHAMADAS\"");
        assertThat(outroIp.getResponse().getStatusCode()).isNull();
        assertThat(repassadas).hasValue(4);
    }

    @Test
    @DisplayName("a janela vira depois de um minuto")
    void janelaDeUmMinuto() {
        LimitePorIpGlobalFilter filtro = new LimitePorIpGlobalFilter(1, relogio);
        chamar(filtro, doIp("200.1.1.1"));
        relogio.andar(Duration.ofSeconds(45));
        MockServerWebExchange recusada = chamar(filtro, doIp("200.1.1.1"));
        relogio.andar(Duration.ofSeconds(15));
        MockServerWebExchange novaJanela = chamar(filtro, doIp("200.1.1.1"));

        assertThat(recusada.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("15");
        assertThat(novaJanela.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("IPv6 conta pelo /64: trocar o fim do endereco nao escapa do limite (D-161)")
    void ipv6PorRede() {
        LimitePorIpGlobalFilter filtro = new LimitePorIpGlobalFilter(1, relogio);
        chamar(filtro, doIp("2001:db8:1:2::1"));

        MockServerWebExchange mesmoBloco = chamar(filtro, doIp("2001:db8:1:2:ffff::9"));
        MockServerWebExchange outroBloco = chamar(filtro, doIp("2001:db8:1:3::1"));

        assertThat(mesmoBloco.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(outroBloco.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(LimitePorIpGlobalFilter.porRede("200.1.1.1")).isEqualTo("200.1.1.1");
    }

    @Test
    @DisplayName("o IP e o que o Caddy escreveu (a direita), nao o que o cliente inventou")
    void ipDaDireita() {
        LimitePorIpGlobalFilter filtro = new LimitePorIpGlobalFilter(1, relogio);
        chamar(filtro, doIp("1.1.1.1, 200.1.1.1"));

        MockServerWebExchange trocouAEsquerda = chamar(filtro, doIp("9.9.9.9, 200.1.1.1"));

        assertThat(trocouAEsquerda.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("sem X-Forwarded-For vale o endereco da conexao; zero desliga o teto")
    void semCabecalhoEDesligado() {
        MockServerHttpRequest.BaseBuilder<?> direto = MockServerHttpRequest.get("/api/x")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 5000));
        assertThat(LimitePorIpGlobalFilter.ipDeOrigem(direto.build())).isEqualTo("127.0.0.1");
        assertThat(LimitePorIpGlobalFilter.ipDeOrigem(MockServerHttpRequest.get("/api/x").build())).isEqualTo("-");

        LimitePorIpGlobalFilter desligado = new LimitePorIpGlobalFilter(0, relogio);
        for (int i = 0; i < 5; i++) {
            chamar(desligado, direto);
        }
        assertThat(repassadas).hasValue(5);
    }

    @Test
    @DisplayName("muitos IPs: as janelas vencidas saem da memoria")
    void limpaJanelasVencidas() {
        LimitePorIpGlobalFilter filtro = new LimitePorIpGlobalFilter(10, relogio);
        for (int i = 0; i <= 50_000; i++) {
            chamar(filtro, doIp("10.0." + (i / 256) + "." + (i % 256) + "-" + i));
        }
        relogio.andar(Duration.ofMinutes(2));

        chamar(filtro, doIp("200.1.1.1"));
        MockServerWebExchange voltou = chamar(filtro, doIp("10.0.0.0-0"));

        assertThat(voltou.getResponse().getStatusCode()).isNull();
    }

    /** Relogio que so anda quando o teste manda. */
    private static final class RelogioDeTeste extends Clock {
        private Instant agora = Instant.parse("2026-10-19T12:00:00Z");

        void andar(Duration quanto) {
            agora = agora.plus(quanto);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
