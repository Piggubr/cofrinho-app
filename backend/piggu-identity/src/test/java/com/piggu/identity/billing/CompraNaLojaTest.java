package com.piggu.identity.billing;

import com.piggu.common.dados.Consentimentos;
import com.piggu.common.security.Plano;
import com.piggu.identity.domain.FamiliaService;
import com.piggu.identity.domain.UserAccount;
import com.piggu.identity.domain.UserAccountRepository;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Compra no app avisada pelo RevenueCat: segredo, idempotencia e ordem dos avisos. */
@AutoConfigureMockMvc
@TestPropertySource(properties = "piggu.revenuecat.webhook-secret=segredo-rc")
class CompraNaLojaTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FamiliaService familias;

    @Autowired
    private UserAccountRepository usuarios;

    @Autowired
    private AssinaturaService assinaturas;

    private UserAccount conta;

    @BeforeEach
    void preparar() {
        usuarios.deleteAll();
        conta = familias.criarConta("loja@piggu.test", "Loja", Consentimentos.VERSAO_DO_AVISO);
    }

    @Test
    @DisplayName("sem o segredo certo o aviso e recusado e nada muda")
    void segredoErrado() throws Exception {
        String compra = aviso("evt-1", "INITIAL_PURCHASE", "APP_STORE", Instant.now());

        avisar("app-store", compra, null).andExpect(status().isUnauthorized());
        avisar("app-store", compra, "Bearer outro").andExpect(status().isUnauthorized());
        assertThat(plano()).isEqualTo(Plano.GRATUITO);
    }

    @Test
    @DisplayName("compra vira Premium da familia pela loja; o mesmo aviso de novo nao conta outra vez")
    void compraIdempotente() throws Exception {
        avisar("app-store", aviso("evt-2", "INITIAL_PURCHASE", "APP_STORE", Instant.now()), "Bearer segredo-rc")
                .andExpect(status().isOk());
        assertThat(plano()).isEqualTo(Plano.PREMIUM);
        assertThat(assinaturas.plano(conta.getId()).origem()).isEqualTo("APP_STORE");

        // Reenvio do mesmo id com outro conteudo: ignorado.
        avisar("app-store", aviso("evt-2", "EXPIRATION", "APP_STORE", Instant.now().plusSeconds(60)), "segredo-rc")
                .andExpect(status().isOk());
        assertThat(plano()).isEqualTo(Plano.PREMIUM);
    }

    @Test
    @DisplayName("expiracao encerra; aviso antigo que chega depois nao reativa")
    void expiracaoEOrdem() throws Exception {
        Instant agora = Instant.now();
        avisar("play-store", aviso("evt-3", "INITIAL_PURCHASE", "PLAY_STORE", agora), "segredo-rc").andExpect(status().isOk());
        avisar("play-store", aviso("evt-4", "EXPIRATION", "PLAY_STORE", agora.plusSeconds(60)), "segredo-rc")
                .andExpect(status().isOk());
        avisar("play-store", aviso("evt-5", "RENEWAL", "PLAY_STORE", agora.plusSeconds(30)), "segredo-rc")
                .andExpect(status().isOk());

        assertThat(plano()).isEqualTo(Plano.GRATUITO);
    }

    @Test
    @DisplayName("aviso de uma loja no caminho de outra e recusado; loja desconhecida e 404")
    void lojaTrocada() throws Exception {
        avisar("play-store", aviso("evt-6", "INITIAL_PURCHASE", "APP_STORE", Instant.now()), "segredo-rc")
                .andExpect(status().is4xxClientError());
        avisar("loja-x", aviso("evt-7", "INITIAL_PURCHASE", "APP_STORE", Instant.now()), "segredo-rc")
                .andExpect(status().isNotFound());
        assertThat(plano()).isEqualTo(Plano.GRATUITO);
    }

    private Plano plano() {
        return assinaturas.plano(conta.getId()).plano();
    }

    private org.springframework.test.web.servlet.ResultActions avisar(String loja, String corpo, String autorizacao)
            throws Exception {
        var pedido = post("/api/billing/stores/" + loja + "/purchases")
                .contentType(MediaType.APPLICATION_JSON).content(corpo);
        if (autorizacao != null) {
            pedido.header("Authorization", autorizacao);
        }
        return mockMvc.perform(pedido);
    }

    private String aviso(String id, String tipo, String loja, Instant momento) {
        return """
                {"api_version":"1.0","event":{"id":"%s","type":"%s","store":"%s","environment":"PRODUCTION",
                 "app_user_id":"%s","period_type":"NORMAL","purchased_at_ms":%d,"event_timestamp_ms":%d,
                 "expiration_at_ms":%d}}
                """.formatted(id + "-" + conta.getId(), tipo, loja, conta.getId(), momento.toEpochMilli(),
                momento.toEpochMilli(), momento.plus(Duration.ofDays(30)).toEpochMilli());
    }
}
