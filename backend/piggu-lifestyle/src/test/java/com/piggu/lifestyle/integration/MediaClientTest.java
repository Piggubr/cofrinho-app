package com.piggu.lifestyle.integration;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UpstreamException;
import com.piggu.common.web.CorrelacaoFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Fotos dos lugares no piggu-media: o token de quem chamou vai junto, e apagar nunca falha. */
class MediaClientTest {

    private static final UUID FOTO = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

    private MockRestServiceServer media;
    private MediaClient cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        media = MockRestServiceServer.bindTo(builder).build();
        cliente = new MediaClient(builder, "http://media.test");
        Jwt jwt = Jwt.withTokenValue("token-da-pessoa").header("alg", "none").subject("p1").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    @DisplayName("envia a foto com o token e a requisicao de quem chamou e devolve o id")
    void enviaAFoto() {
        MDC.put(CorrelacaoFilter.MDC_REQUISICAO, "req-1");
        media.expect(requestTo("http://media.test/api/assets"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-da-pessoa"))
                .andExpect(header(CorrelacaoFilter.CABECALHO, "req-1"))
                .andExpect(jsonPath("$.mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.contexto").value("lugar"))
                .andRespond(withSuccess("{\"id\":\"" + FOTO + "\"}", MediaType.APPLICATION_JSON));

        assertThat(cliente.enviar("aGVsbG8=", null, "lugar")).isEqualTo(FOTO);
        media.verify();
    }

    @Test
    @DisplayName("foto recusada pelo media volta com o motivo e o status dele, nao como media fora do ar")
    void fotoRecusada() {
        media.expect(requestTo("http://media.test/api/assets"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"erro\":\"A foto precisa ser JPEG, PNG ou WebP.\",\"codigo\":\"BusinessException\"}"));

        assertThatThrownBy(() -> cliente.enviar("aGVsbG8=", "image/heic", "lugar"))
                .isInstanceOfSatisfying(BusinessException.class, erro -> {
                    assertThat(erro).isNotInstanceOf(UpstreamException.class);
                    assertThat(erro.getMessage()).isEqualTo("A foto precisa ser JPEG, PNG ou WebP.");
                    assertThat(erro.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                });
    }

    @Test
    @DisplayName("resposta sem id e erro de servico externo")
    void respostaSemId() {
        media.expect(requestTo("http://media.test/api/assets"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.enviar("aGVsbG8=", "image/png", "lugar"))
                .isInstanceOf(UpstreamException.class);
    }

    @Test
    @DisplayName("sem sessao nao fala com o media")
    void semSessao() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> cliente.enviar("aGVsbG8=", "image/png", "lugar"))
                .isInstanceOf(UpstreamException.class)
                .hasMessageContaining("Sessao ausente");
    }

    @Test
    @DisplayName("apaga a foto pelo id")
    void apagaAFoto() {
        media.expect(requestTo("http://media.test/api/assets/" + FOTO))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Authorization", "Bearer token-da-pessoa"))
                .andRespond(withSuccess());

        cliente.apagar(FOTO);
        media.verify();
    }

    @Test
    @DisplayName("falha ao apagar so fica no log, e sem foto nao ha o que apagar")
    void falhaAoApagar() {
        media.expect(requestTo("http://media.test/api/assets/" + FOTO)).andRespond(withServerError());

        assertThatCode(() -> cliente.apagar(FOTO)).doesNotThrowAnyException();
        assertThatCode(() -> cliente.apagar(null)).doesNotThrowAnyException();
        media.verify();
    }
}
