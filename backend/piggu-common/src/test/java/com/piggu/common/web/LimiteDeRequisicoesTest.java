package com.piggu.common.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** Limite de chamadas por IP e por conta, e de tamanho do corpo. */
class LimiteDeRequisicoesTest {

    private static final LimiteDeRequisicoes.Limites LIMITES =
            new LimiteDeRequisicoes.Limites(3, 3, 3, 2, 2, 1000, 5000, 1);

    private final MutableClock relogio = new MutableClock();
    private final LimiteDeRequisicoes antes = new LimiteDeRequisicoes(
            LimiteDeRequisicoes.Etapa.ANTES_DO_LOGIN, LIMITES, new ObjectMapper(), relogio);
    private final LimiteDeRequisicoes depois = new LimiteDeRequisicoes(
            LimiteDeRequisicoes.Etapa.DEPOIS_DO_LOGIN, LIMITES, new ObjectMapper(), relogio);

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("login: X-Forwarded-For forjado diferente a cada tentativa nao escapa do limite")
    void forjarCabecalhoNaoAdianta() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(login("10.0.0." + i + ", 203.0.113.9").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse quarta = login("1.2.3.4, 203.0.113.9");
        assertThat(quarta.getStatus()).isEqualTo(429);
        assertThat(quarta.getHeader("Retry-After")).isNotBlank();
        assertThat(quarta.getContentAsString()).contains("LIMITE_DE_CHAMADAS");

        assertThat(login("203.0.113.10").getStatus()).as("outro IP tem contador proprio").isEqualTo(200);
    }

    @Test
    @DisplayName("a janela de um minuto passa e o login volta a responder")
    void janelaPassa() throws Exception {
        for (int i = 0; i < 4; i++) {
            login("203.0.113.9");
        }
        relogio.avancar(61_000);
        assertThat(login("203.0.113.9").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("escrita e por conta: quem passa do limite para, a outra conta segue; leitura nao conta")
    void escritaPorConta() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(escrever("ana", "/api/expenses").getStatus()).isEqualTo(200);
        }
        assertThat(escrever("ana", "/api/expenses").getStatus()).isEqualTo(429);
        assertThat(escrever("bia", "/api/expenses").getStatus()).isEqualTo(200);

        MockHttpServletRequest leitura = new MockHttpServletRequest("GET", "/api/expenses");
        logar("ana");
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        depois.doFilter(leitura, resposta, new MockFilterChain());
        assertThat(resposta.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("envio de foto tem limite proprio, menor que o de escrita")
    void envioDeFoto() throws Exception {
        assertThat(escrever("ana", "/api/receipts/parse").getStatus()).isEqualTo(200);
        assertThat(escrever("ana", "/api/receipts/parse").getStatus()).isEqualTo(200);
        assertThat(escrever("ana", "/api/receipts/parse").getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("extrato conta como envio de arquivo: corpo maior e limite de envio")
    void extrato() throws Exception {
        MockHttpServletRequest extrato = new MockHttpServletRequest("POST", "/api/expenses/import/preview");
        extrato.setContent(new byte[4000]);
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        antes.doFilter(extrato, resposta, new MockFilterChain());
        assertThat(resposta.getStatus()).isEqualTo(200);

        assertThat(escrever("ana", "/api/expenses/import").getStatus()).isEqualTo(200);
        assertThat(escrever("ana", "/api/expenses/import/preview").getStatus()).isEqualTo(200);
        assertThat(escrever("ana", "/api/expenses/import").getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("busca que chama servico de fora tem limite por conta; leitura comum segue livre")
    void consultaQueCusta() throws Exception {
        assertThat(ler("ana", "/api/movies/search").getStatus()).isEqualTo(200);
        assertThat(ler("ana", "/api/exchange-rate").getStatus()).isEqualTo(200);
        assertThat(ler("ana", "/api/shopping/catalog").getStatus()).isEqualTo(429);
        assertThat(ler("bia", "/api/shopping/catalog").getStatus()).isEqualTo(200);
        for (int i = 0; i < 5; i++) {
            assertThat(ler("ana", "/api/expenses").getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("corpo acima do limite e recusado com 413 antes de chegar ao servico")
    void corpoGrande() throws Exception {
        MockHttpServletRequest grande = new MockHttpServletRequest("POST", "/api/expenses");
        grande.setContent(new byte[1001]);
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        antes.doFilter(grande, resposta, new MockFilterChain());
        assertThat(resposta.getStatus()).isEqualTo(413);

        MockHttpServletRequest foto = new MockHttpServletRequest("POST", "/api/assets");
        foto.setContent(new byte[4000]);
        resposta = new MockHttpServletResponse();
        antes.doFilter(foto, resposta, new MockFilterChain());
        assertThat(resposta.getStatus()).as("foto tem teto maior").isEqualTo(200);
    }

    private MockHttpServletResponse login(String xForwardedFor) throws Exception {
        MockHttpServletRequest pedido = new MockHttpServletRequest("POST", "/api/auth/google");
        pedido.addHeader("X-Forwarded-For", xForwardedFor);
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        antes.doFilter(pedido, resposta, new MockFilterChain());
        return resposta;
    }

    private MockHttpServletResponse escrever(String conta, String rota) throws Exception {
        logar(conta);
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        depois.doFilter(new MockHttpServletRequest("POST", rota), resposta, new MockFilterChain());
        return resposta;
    }

    private MockHttpServletResponse ler(String conta, String rota) throws Exception {
        logar(conta);
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        depois.doFilter(new MockHttpServletRequest("GET", rota), resposta, new MockFilterChain());
        return resposta;
    }

    private static void logar(String conta) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(conta).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    /** Relogio que o teste empurra para frente. */
    private static final class MutableClock extends Clock {
        private Instant agora = Instant.parse("2026-10-08T12:00:00Z");

        void avancar(long milis) {
            agora = agora.plusMillis(milis);
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
