package com.piggu.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Servico fora do ar ou lento (BUG-005 e BUG-006 da auditoria): o gateway responde com o
 * corpo de erro de sempre, 503 ou 504, em vez de um 500 cru ou de ficar pendurado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.gateway.server.webflux.httpclient.response-timeout=1s",
        "piggu.open-finance.ligado=false"})
@AutoConfigureWebTestClient(timeout = "20s")
class ServicoForaDoArTest {

    /** Aceita a conexao e nunca responde, como o rewards pausado da auditoria. */
    private static final HttpServer LENTO = servidorLento();

    @Autowired
    private WebTestClient web;

    @Autowired
    private RouteLocator rotas;

    @DynamicPropertySource
    static void enderecos(DynamicPropertyRegistry registro) {
        registro.add("REWARDS_URL", () -> "http://localhost:" + LENTO.getAddress().getPort());
        // Nome que nao resolve, como o banking sem o perfil open-finance.
        registro.add("MEDIA_URL", () -> "http://servico-que-nao-existe.invalid:8085");
        // Porta fechada: conexao recusada.
        registro.add("FINANCE_URL", () -> "http://localhost:1");
    }

    @AfterAll
    static void parar() {
        LENTO.stop(0);
    }

    @Test
    @DisplayName("conexao recusada: 503 SERVICO_INDISPONIVEL no corpo de sempre")
    void conexaoRecusada() {
        web.get().uri("/api/expenses").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("SERVICO_INDISPONIVEL")
                .jsonPath("$.erro").isEqualTo("Essa parte do Piggu está fora do ar agora. Tente de novo em instantes.")
                .jsonPath("$.campos").isArray()
                .jsonPath("$.momento").exists();
    }

    @Test
    @DisplayName("nome do servico que nao resolve: 503")
    void nomeQueNaoResolve() {
        web.get().uri("/api/feed").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.codigo").isEqualTo("SERVICO_INDISPONIVEL");
    }

    @Test
    @DisplayName("servico que nao responde: 504 dentro do prazo, com o mesmo codigo")
    void tempoEsgotado() {
        long inicio = System.nanoTime();
        web.get().uri("/api/coins").exchange()
                .expectStatus().isEqualTo(504)
                .expectBody().jsonPath("$.codigo").isEqualTo("SERVICO_INDISPONIVEL");
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("Open Finance desligado: /api/banking/status responde sem chamar o banking")
    void bankingDesligado() {
        web.get().uri("/api/banking/status").exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"habilitado\":false}");
        // As outras rotas do banking seguem para o servico (aqui, fora do ar).
        web.get().uri("/api/banking/accounts").exchange().expectStatus().isEqualTo(503);
    }

    @Test
    @DisplayName("prazo maior so nas rotas lentas de verdade")
    void prazosPorRota() {
        Map<String, Object> prazos = rotas.getRoutes().collectList().block().stream()
                .filter(rota -> rota.getMetadata().containsKey("response-timeout"))
                .collect(Collectors.toMap(Route::getId, rota -> rota.getMetadata().get("response-timeout")));

        assertThat(prazos.keySet()).containsExactlyInAnyOrder("finance-lento", "identity-lento", "banking");
    }

    private static HttpServer servidorLento() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/", troca -> {
                try {
                    Thread.sleep(30_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                troca.close();
            });
            servidor.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
