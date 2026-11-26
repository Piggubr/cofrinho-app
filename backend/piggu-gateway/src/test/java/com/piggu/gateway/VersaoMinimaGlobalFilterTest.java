package com.piggu.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersaoMinimaGlobalFilterTest {

    private final AtomicInteger repassadas = new AtomicInteger();

    private MockServerWebExchange chamar(VersaoMinimaGlobalFilter filtro, String agente) {
        MockServerHttpRequest.BaseBuilder<?> pedido = MockServerHttpRequest.get("/api/expenses");
        if (agente != null) {
            pedido.header("User-Agent", agente);
        }
        MockServerWebExchange troca = MockServerWebExchange.from(pedido);
        filtro.filter(troca, adiante -> {
            repassadas.incrementAndGet();
            return Mono.empty();
        }).block();
        return troca;
    }

    @Test
    @DisplayName("abaixo da minima: 426 com ATUALIZE_O_APP e o corpo de erro de sempre")
    void recusaVersaoVelha() {
        VersaoMinimaGlobalFilter filtro = new VersaoMinimaGlobalFilter("2.0.1", "2.1");

        MockServerWebExchange android = chamar(filtro, "Piggu-Android/2.0.0");
        MockServerWebExchange ios = chamar(filtro, "Piggu-iOS/2.0.9");

        assertThat(android.getResponse().getStatusCode()).isEqualTo(HttpStatus.UPGRADE_REQUIRED);
        assertThat(android.getResponse().getBodyAsString().block())
                .contains("\"codigo\":\"ATUALIZE_O_APP\"")
                .contains("\"erro\":\"" + VersaoMinimaGlobalFilter.MENSAGEM + "\"");
        assertThat(ios.getResponse().getStatusCode()).isEqualTo(HttpStatus.UPGRADE_REQUIRED);
        assertThat(repassadas).hasValue(0);
    }

    @Test
    @DisplayName("na minima ou acima passa, e 2.0 vale o mesmo que 2.0.0")
    void passaNaMinimaOuAcima() {
        VersaoMinimaGlobalFilter filtro = new VersaoMinimaGlobalFilter("2.0.0", "2.0");

        assertThat(chamar(filtro, "Piggu-Android/2.0.0").getResponse().getStatusCode()).isNull();
        assertThat(chamar(filtro, "Piggu-Android/2.10.0").getResponse().getStatusCode()).isNull();
        assertThat(chamar(filtro, "Piggu-iOS/2.0.0").getResponse().getStatusCode()).isNull();
        assertThat(chamar(filtro, "Piggu-iOS/3").getResponse().getStatusCode()).isNull();
        assertThat(repassadas).hasValue(4);
    }

    @Test
    @DisplayName("o sufixo da variante (2.0.0-demo) nao atrapalha a leitura")
    void sufixoDaVariante() {
        VersaoMinimaGlobalFilter filtro = new VersaoMinimaGlobalFilter("2.0.1", null);

        assertThat(chamar(filtro, "Piggu-Android/2.0.0-demo").getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UPGRADE_REQUIRED);
        assertThat(chamar(filtro, "Piggu-Android/2.0.1-demo").getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("site, app antigo, sem User-Agent e plataforma sem minima passam")
    void outrosClientesPassam() {
        VersaoMinimaGlobalFilter filtro = new VersaoMinimaGlobalFilter("9.0.0", null);

        chamar(filtro, "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) Piggu-Android/1.0.0");
        chamar(filtro, null);
        chamar(filtro, "Piggu-iOS/1.0.0");
        chamar(filtro, "Piggu-Windows/1.0.0");

        assertThat(repassadas).hasValue(4);
    }

    @Test
    @DisplayName("sem nenhuma minima, desligado: nem le o User-Agent")
    void desligadoPorPadrao() {
        VersaoMinimaGlobalFilter filtro = new VersaoMinimaGlobalFilter("", " ");

        assertThat(chamar(filtro, "Piggu-Android/0.0.1").getResponse().getStatusCode()).isNull();
        assertThat(chamar(filtro, "Piggu-iOS/0.0.1").getResponse().getStatusCode()).isNull();
        assertThat(repassadas).hasValue(2);
    }

    @Test
    @DisplayName("minima que nao e versao derruba a subida, com o nome da variavel")
    void minimaInvalida() {
        assertThatThrownBy(() -> new VersaoMinimaGlobalFilter("2.0.0-beta", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PIGGU_VERSAO_MINIMA_ANDROID");
        assertThatThrownBy(() -> new VersaoMinimaGlobalFilter(null, "dois"))
                .hasMessageContaining("PIGGU_VERSAO_MINIMA_IOS");
    }

    @Test
    @DisplayName("comparacao numero a numero, nao como texto")
    void comparacao() {
        assertThat(VersaoMinimaGlobalFilter.comparar(new int[] {2, 10}, new int[] {2, 9})).isPositive();
        assertThat(VersaoMinimaGlobalFilter.comparar(new int[] {2}, new int[] {2, 0, 0})).isZero();
        assertThat(VersaoMinimaGlobalFilter.comparar(new int[] {1, 99}, new int[] {2})).isNegative();
    }
}
