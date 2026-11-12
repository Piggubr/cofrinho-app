package com.piggu.gateway;

import io.netty.channel.ConnectTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;
import reactor.core.Exceptions;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Que falha vira 503, que falha vira 504 e o que segue para o tratador padrao (D-191). */
class RespostasDeServicoForaDoArTest {

    private final ServicoForaDoAr tratador = new ServicoForaDoAr();

    @Test
    @DisplayName("tempo esgotado em qualquer forma vira 504, mesmo embrulhado")
    void tempoEsgotado() {
        assertThat(ServicoForaDoAr.statusPara(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT)))
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(ServicoForaDoAr.statusPara(new RuntimeException(new TimeoutException())))
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        // ConnectTimeoutException e um ConnectException: o prazo vence antes do "fora do ar".
        assertThat(ServicoForaDoAr.statusPara(new ConnectTimeoutException("2 s")))
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    @DisplayName("conexao recusada, nome que nao resolve e conexao fechada no meio viram 503")
    void foraDoAr() {
        assertThat(ServicoForaDoAr.statusPara(new IllegalStateException(new ConnectException("recusada"))))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ServicoForaDoAr.statusPara(new UnknownHostException("banking")))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ServicoForaDoAr.statusPara(PrematureCloseException.TEST_EXCEPTION))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("outro erro, ou outro status, segue para o tratador padrao do Spring")
    void resto() {
        IllegalArgumentException outro = new IllegalArgumentException("bug");
        assertThat(ServicoForaDoAr.statusPara(outro)).isNull();
        assertThat(ServicoForaDoAr.statusPara(new ResponseStatusException(HttpStatus.NOT_FOUND))).isNull();

        MockServerWebExchange troca = MockServerWebExchange.from(MockServerHttpRequest.get("/api/coins"));
        assertThatThrownBy(() -> tratador.handle(troca, outro).block()).isSameAs(outro);
        assertThat(troca.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("resposta ja comecada nao e reescrita: o erro segue adiante")
    void respostaJaComecada() {
        MockServerWebExchange troca = MockServerWebExchange.from(MockServerHttpRequest.get("/api/coins"));
        troca.getResponse().setComplete().block();
        ConnectException queda = new ConnectException("caiu");

        assertThatThrownBy(() -> tratador.handle(troca, queda).block())
                .satisfies(e -> assertThat(Exceptions.unwrap(e)).isSameAs(queda));
    }

    @Test
    @DisplayName("Open Finance ligado, ou outra rota do banking, segue para o servico")
    void bankingLigadoOuOutraRota() {
        AtomicBoolean repassou = new AtomicBoolean();
        BankingDesligadoGlobalFilter ligado = new BankingDesligadoGlobalFilter(true);
        BankingDesligadoGlobalFilter desligado = new BankingDesligadoGlobalFilter(false);

        ligado.filter(MockServerWebExchange.from(MockServerHttpRequest.get("/api/banking/status")), t -> {
            repassou.set(true);
            return Mono.empty();
        }).block();
        assertThat(repassou).isTrue();

        repassou.set(false);
        desligado.filter(MockServerWebExchange.from(MockServerHttpRequest.post("/api/banking/status")), t -> {
            repassou.set(true);
            return Mono.empty();
        }).block();
        assertThat(repassou).as("so o GET do status responde no gateway").isTrue();

        MockServerWebExchange status = MockServerWebExchange.from(MockServerHttpRequest.get("/api/banking/status"));
        desligado.filter(status, t -> Mono.error(new AssertionError("nao devia chamar o banking"))).block();
        assertThat(status.getResponse().getBodyAsString().block()).isEqualTo("{\"habilitado\":false}");
    }
}
