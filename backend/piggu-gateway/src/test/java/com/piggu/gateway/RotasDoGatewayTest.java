package com.piggu.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.web.reactive.server.WebTestClient;

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

    @Test
    @DisplayName("atuador de rotas nao esta exposto; o de saude sim")
    void atuadorFechado() {
        web.get().uri("/actuator/gateway/routes").exchange().expectStatus().isNotFound();
        web.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }
}
