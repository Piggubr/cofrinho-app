package com.piggu.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O contrato da API (docs/api) e o que o app alcanca pelo gateway, nem mais nem menos.
 *
 * <p>Toda rota do contrato tem que passar pelo gateway, senao o app nativo chamaria um
 * endereco que da 404. E o proprio contrato (/v3/api-docs, Swagger UI) nunca passa: em
 * producao ele fica desligado nos servicos, e o gateway nao o roteia.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ContratoNoGatewayTest {

    private static final List<String> SERVICOS = List.of("identity", "finance", "rewards", "lifestyle", "media", "banking");

    @Autowired
    private RouteLocator rotas;

    @Autowired
    private WebTestClient web;

    @Test
    @DisplayName("toda rota do contrato chega ao servico certo pelo gateway")
    void contratoRoteado() {
        List<Route> todas = rotas.getRoutes().collectList().block();
        List<String> fora = new ArrayList<>();
        for (String servico : SERVICOS) {
            for (String caminho : caminhos(servico)) {
                String concreto = caminho.replaceAll("\\{[^}]+}", "x");
                String destino = Flux.fromIterable(todas)
                        .filterWhen(rota -> rota.getPredicate().apply(
                                MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.GET, concreto))))
                        .map(Route::getId)
                        .blockFirst();
                if (!servico.equals(destino)) {
                    fora.add(servico + " " + caminho + " -> " + destino);
                }
            }
        }
        assertThat(fora).as("rotas do contrato que o gateway nao manda para o servico dono").isEmpty();
    }

    @Test
    @DisplayName("o contrato e o Swagger UI nao passam pelo gateway")
    void contratoNaoExposto() {
        for (String rota : List.of("/v3/api-docs", "/v3/api-docs.yaml", "/swagger-ui.html", "/swagger-ui/index.html")) {
            web.get().uri(rota).exchange().expectStatus().isNotFound();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> caminhos(String servico) {
        String pasta = System.getProperty("piggu.contrato.pasta", "../../docs/api");
        File arquivo = new File(pasta, servico + ".yaml");
        assertThat(arquivo).as("contrato de " + servico).exists();
        YamlMapFactoryBean yaml = new YamlMapFactoryBean();
        yaml.setResources(new FileSystemResource(arquivo));
        Map<String, Object> contrato = yaml.getObject();
        return new ArrayList<>(((Map<String, Object>) contrato.get("paths")).keySet());
    }
}
