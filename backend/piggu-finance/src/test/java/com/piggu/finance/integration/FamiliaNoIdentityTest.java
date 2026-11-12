package com.piggu.finance.integration;

import com.piggu.common.error.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** A conferencia da familia no identity, so para o dividirCom com quem o finance nao viu (D-192). */
class FamiliaNoIdentityTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://identity.test");
    private final MockRestServiceServer identity = MockRestServiceServer.bindTo(builder).build();
    private final FamiliaNoIdentity familia = new FamiliaNoIdentity(builder.build());

    @BeforeEach
    void comToken() {
        Jwt jwt = Jwt.withTokenValue("token-de-quem-divide").header("alg", "none").subject(UUID.randomUUID().toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("repassa o token de quem divide e devolve os ids dos membros")
    void membros() {
        UUID ana = UUID.randomUUID();
        UUID beto = UUID.randomUUID();
        identity.expect(requestTo("http://identity.test/api/family"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-de-quem-divide"))
                .andRespond(withSuccess("{\"id\":\"" + UUID.randomUUID() + "\",\"membros\":[{\"id\":\"" + ana
                        + "\",\"nome\":\"Ana\"},{\"id\":\"" + beto + "\",\"nome\":\"Beto\"}]}", MediaType.APPLICATION_JSON));

        assertThat(familia.membros()).containsExactlyInAnyOrder(ana, beto);
        identity.verify();
    }

    @Test
    @DisplayName("identity com erro ou fora do ar vira 503 SERVICO_INDISPONIVEL")
    void foraDoAr() {
        identity.expect(requestTo("http://identity.test/api/family")).andRespond(withServerError());
        identity.expect(requestTo("http://identity.test/api/family")).andRespond(pedido -> {
            throw new IOException("conexao recusada");
        });

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(familia::membros).isInstanceOf(BusinessException.class)
                    .hasMessage("Essa parte do Piggu está fora do ar agora. Tente de novo em instantes.")
                    .extracting("codigo").isEqualTo("SERVICO_INDISPONIVEL");
        }
        identity.verify();
    }

    @Test
    @DisplayName("sem token na requisicao, nem chama o identity")
    void semToken() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(familia::membros).isInstanceOf(BusinessException.class)
                .extracting("codigo").isEqualTo("SERVICO_INDISPONIVEL");
        identity.verify();
    }
}
