package com.piggu.common.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelacaoFilterTest {

    private final CorrelacaoFilter filtro = new CorrelacaoFilter();

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    /** Executa o filtro e devolve o MDC como estava durante a requisicao. */
    private Map<String, String> executar(MockHttpServletRequest pedido, MockHttpServletResponse resposta)
            throws Exception {
        Map<String, String> visto = new HashMap<>();
        filtro.doFilter(pedido, resposta, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                visto.putAll(MDC.getCopyOfContextMap() == null ? Map.of() : MDC.getCopyOfContextMap());
            }
        });
        return visto;
    }

    @Test
    @DisplayName("repassa o id do gateway e o devolve na resposta")
    void repassaIdDoGateway() throws Exception {
        MockHttpServletRequest pedido = new MockHttpServletRequest("GET", "/api/expenses");
        pedido.addHeader("X-Request-Id", "abc-123");
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        Map<String, String> mdc = executar(pedido, resposta);

        assertThat(mdc).containsEntry("requestId", "abc-123");
        assertThat(resposta.getHeader("X-Request-Id")).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("id com quebra de linha ou longo demais e trocado: nao injeta linha falsa no log")
    void recusaIdForjado() throws Exception {
        MockHttpServletRequest pedido = new MockHttpServletRequest("GET", "/api/expenses");
        pedido.addHeader("X-Request-Id", "x\nINFO usuario=admin");
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        Map<String, String> mdc = executar(pedido, resposta);

        assertThat(mdc.get("requestId")).matches("[0-9a-f-]{36}");
    }

    @Test
    @DisplayName("usuario entra no log so pelo id, e o MDC e limpo no fim")
    void usuarioPorIdEMdcLimpo() throws Exception {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("1111-2222")
                .claim("email", "beatriz@piggu.test").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        Map<String, String> mdc = executar(new MockHttpServletRequest("GET", "/api/x"), new MockHttpServletResponse());

        assertThat(mdc).containsEntry("userId", "1111-2222");
        assertThat(mdc.values()).noneMatch(valor -> valor.contains("@"));
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("userId")).isNull();
    }
}
