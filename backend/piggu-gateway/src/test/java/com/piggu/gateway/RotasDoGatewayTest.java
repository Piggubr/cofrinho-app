package com.piggu.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.RemoveXForwardedHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Roteamento do gateway, que ate aqui so tinha sido conferido a mao.
 *
 * <p>Uma rota a menos e um servico inteiro some do app sem erro na subida; o atuador
 * "gateway" exposto deixa qualquer um criar rotas. Os dois casos quebram aqui.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RotasDoGatewayTest {

    @Autowired
    private RouteLocator rotas;

    @Autowired
    private WebTestClient web;

    @Test
    @DisplayName("todo servico tem rota, inclusive o banking opcional")
    void todasAsRotas() {
        List<String> ids = rotas.getRoutes().map(Route::getId).collectList().block();

        assertThat(ids).containsExactlyInAnyOrder(
                "identity", "finance", "rewards", "lifestyle", "media", "banking");
    }

    @Autowired
    private List<HttpHeadersFilter> filtrosDeCabecalho;

    @Test
    @DisplayName("o IP do cliente chega aos servicos no X-Forwarded-For, com o do proxy a direita")
    void repassaOIpDoCliente() {
        assertThat(filtrosDeCabecalho).noneMatch(RemoveXForwardedHeadersFilter.class::isInstance);
        HttpHeadersFilter xff = filtrosDeCabecalho.stream()
                .filter(XForwardedHeadersFilter.class::isInstance).findFirst().orElseThrow();
        // O Caddy (172.18.0.5) chama com o IP real que ele escreveu.
        MockServerWebExchange troca = MockServerWebExchange.from(MockServerHttpRequest.get("/api/auth/entrar")
                .remoteAddress(new InetSocketAddress("172.18.0.5", 40000))
                .header("X-Forwarded-For", "203.0.113.7"));

        HttpHeaders repassados = xff.filter(troca.getRequest().getHeaders(), troca);

        assertThat(repassados.getFirst("X-Forwarded-For")).isEqualTo("203.0.113.7,172.18.0.5");
    }

    @Test
    @DisplayName("atuador de rotas nao esta exposto; o de saude sim")
    void atuadorFechado() {
        web.get().uri("/actuator/gateway/routes").exchange().expectStatus().isNotFound();
        web.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("metricas para o Prometheus, com as faixas de tempo do p95")
    void metricas() {
        web.get().uri("/actuator/health").exchange();

        web.get().uri("/actuator/prometheus").exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(texto -> assertThat(texto).contains("jvm_memory_used_bytes", "http_server_requests_seconds_bucket"));
    }

    @Test
    @DisplayName("a volta do Google no app passa pelo CORS; o resto segue so com as origens do site")
    void corsDaVoltaDoGoogle() {
        // O identity nao esta de pe no teste: passar pelo CORS e chegar ao repasse (erro 5xx).
        web.post().uri("/api/auth/google/app")
                .header("Origin", "https://accounts.google.com")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .bodyValue("id_token=x&state=y")
                .exchange()
                .expectStatus().is5xxServerError();

        web.post().uri("/api/auth/google")
                .header("Origin", "https://accounts.google.com")
                .exchange()
                .expectStatus().isForbidden();
    }
}
