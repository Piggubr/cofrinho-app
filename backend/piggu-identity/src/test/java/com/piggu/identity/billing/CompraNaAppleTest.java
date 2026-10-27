package com.piggu.identity.billing;

import com.piggu.common.dados.Consentimentos;
import com.piggu.common.security.Plano;
import com.piggu.common.security.PigguRole;
import com.piggu.identity.domain.FamiliaService;
import com.piggu.identity.domain.Household;
import com.piggu.identity.domain.HouseholdRepository;
import com.piggu.identity.domain.UserAccount;
import com.piggu.identity.domain.UserAccountRepository;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Compra no iPhone pelo StoreKit 2: transacao do app e avisos V2 da App Store, assinados como a Apple assina. */
@AutoConfigureMockMvc
class CompraNaAppleTest extends PostgresIntegrationTest {

    private static final AppleDeTeste APPLE = new AppleDeTeste();
    private static final Duration UM_MES = Duration.ofDays(30);

    @DynamicPropertySource
    static void apple(DynamicPropertyRegistry propriedades) {
        propriedades.add("piggu.app-store.raizes", APPLE::raiz);
        propriedades.add("piggu.app-store.checar-online", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FamiliaService familias;

    @Autowired
    private HouseholdRepository casas;

    @Autowired
    private UserAccountRepository usuarios;

    @Autowired
    private AssinaturaService assinaturas;

    private UserAccount conta;

    @BeforeEach
    void preparar() {
        usuarios.deleteAll();
        conta = familias.criarConta("apple@piggu.test", "Apple", Consentimentos.VERSAO_DO_AVISO);
    }

    @Test
    @DisplayName("a transacao que o app manda depois da compra vira Premium da conta, pela App Store")
    void compraNoApp() throws Exception {
        String transacao = APPLE.assinar(APPLE.transacao(conta.getId(), Instant.now(), UM_MES));

        comprar(transacao)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plano").value("PREMIUM"))
                .andExpect(jsonPath("$.origem").value("APP_STORE"))
                .andExpect(jsonPath("$.voceAssinou").value(true));
    }

    @Test
    @DisplayName("transacao que nao foi assinada pela Apple, ou de outro app, e recusada")
    void assinaturaFalsa() throws Exception {
        String deOutraCadeia = new AppleDeTeste().assinar(APPLE.transacao(conta.getId(), Instant.now(), UM_MES));
        comprar(deOutraCadeia).andExpect(status().isUnprocessableContent());

        Map<String, Object> outroApp = APPLE.transacao(conta.getId(), Instant.now(), UM_MES);
        outroApp.put("bundleId", "com.exemplo.outro");
        comprar(APPLE.assinar(outroApp)).andExpect(status().isUnprocessableContent());

        comprar("nao-e-um-jws").andExpect(status().isUnprocessableContent());
        assertThat(plano()).isEqualTo(Plano.GRATUITO);
    }

    @Test
    @DisplayName("a compra de outra conta do Piggu nao da Premium para esta")
    void compraDeOutraConta() throws Exception {
        comprar(APPLE.assinar(APPLE.transacao(UUID.randomUUID(), Instant.now(), UM_MES)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("OUTRA_CONTA"));
        comprar(APPLE.assinar(APPLE.transacao(null, Instant.now(), UM_MES))).andExpect(status().isConflict());
        assertThat(plano()).isEqualTo(Plano.GRATUITO);
    }

    @Test
    @DisplayName("renovacao estende, reembolso encerra; o mesmo aviso de novo nao reativa")
    void renovacaoEReembolso() throws Exception {
        Instant inicio = Instant.now().minus(Duration.ofDays(20));
        String assinou = APPLE.aviso("SUBSCRIBED", "INITIAL_BUY", APPLE.transacao(conta.getId(), inicio, UM_MES), inicio);
        avisar(assinou).andExpect(status().isOk());
        assertThat(plano()).isEqualTo(Plano.PREMIUM);

        Instant renovou = Instant.now().minus(Duration.ofDays(1));
        avisar(APPLE.aviso("DID_RENEW", null, APPLE.transacao(conta.getId(), renovou, UM_MES), renovou))
                .andExpect(status().isOk());
        assertThat(assinaturas.plano(conta.getId(), null).premiumAte())
                .isAfter(Instant.now().plus(Duration.ofDays(28)));

        Map<String, Object> reembolsada = APPLE.transacao(conta.getId(), renovou, UM_MES);
        reembolsada.put("revocationDate", Instant.now().toEpochMilli());
        reembolsada.put("revocationReason", 0);
        avisar(APPLE.aviso("REFUND", null, reembolsada, Instant.now())).andExpect(status().isOk());
        assertThat(plano()).isEqualTo(Plano.GRATUITO);

        avisar(assinou).andExpect(status().isOk());
        assertThat(plano()).isEqualTo(Plano.GRATUITO);
    }

    @Test
    @DisplayName("cancelar so desliga a renovacao; o Premium vai ate expirar, e a expiracao encerra")
    void cancelamentoEExpiracao() throws Exception {
        Instant inicio = Instant.now().minus(Duration.ofDays(10));
        avisar(APPLE.aviso("SUBSCRIBED", "INITIAL_BUY", APPLE.transacao(conta.getId(), inicio, UM_MES), inicio));

        avisar(APPLE.aviso("DID_CHANGE_RENEWAL_STATUS", "AUTO_RENEW_DISABLED",
                APPLE.transacao(conta.getId(), inicio, UM_MES), Instant.now().minusSeconds(60)));
        assertThat(plano()).isEqualTo(Plano.PREMIUM);

        Instant antes = Instant.now().minus(Duration.ofDays(40));
        avisar(APPLE.aviso("EXPIRED", "VOLUNTARY", APPLE.transacao(conta.getId(), antes, UM_MES), Instant.now()));
        assertThat(plano()).isEqualTo(Plano.GRATUITO);
    }

    @Test
    @DisplayName("aviso sem a assinatura da Apple e recusado com 401; o de teste da Apple e aceito sem mudar nada")
    void avisoFalsoEDeTeste() throws Exception {
        String falso = new AppleDeTeste().aviso("SUBSCRIBED", "INITIAL_BUY",
                APPLE.transacao(conta.getId(), Instant.now(), UM_MES), Instant.now());
        avisar(falso).andExpect(status().isUnauthorized());

        Map<String, Object> teste = new java.util.LinkedHashMap<>(Map.of("notificationType", "TEST",
                "notificationUUID", UUID.randomUUID().toString(), "version", "2.0",
                "signedDate", Instant.now().toEpochMilli(),
                "data", Map.of("bundleId", AppleDeTeste.BUNDLE, "environment", "Sandbox")));
        avisar(APPLE.assinar(teste)).andExpect(status().isOk());
        assertThat(plano()).isEqualTo(Plano.GRATUITO);
    }

    @Test
    @DisplayName("quem ja e Premium pelo site continua pelo site: a Apple nao troca nem derruba")
    void premiumPeloSite() throws Exception {
        Household casa = familias.daConta(conta);
        casa.aplicarAssinatura("WEB", Instant.now().plus(UM_MES), Instant.now().minusSeconds(120), null,
                TipoDePlano.INDIVIDUAL, conta.getId());
        casas.save(casa);

        comprar(APPLE.assinar(APPLE.transacao(conta.getId(), Instant.now(), Duration.ofDays(365))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origem").value("WEB"));
        avisar(APPLE.aviso("EXPIRED", "VOLUNTARY",
                APPLE.transacao(conta.getId(), Instant.now().minus(Duration.ofDays(40)), UM_MES), Instant.now()));

        assertThat(plano()).isEqualTo(Plano.PREMIUM);
        assertThat(assinaturas.plano(conta.getId(), null).origem()).isEqualTo("WEB");
    }

    private Plano plano() {
        return assinaturas.plano(conta.getId(), null).plano();
    }

    private ResultActions comprar(String transacao) throws Exception {
        return mockMvc.perform(post("/api/billing/app-store/transactions")
                .with(jwt().jwt(token -> token.subject(conta.getId().toString())
                                .claim("email", conta.getEmail())
                                .claim("role", PigguRole.TITULAR.name())
                                .claim("familia", conta.getHouseholdId().toString()))
                        .authorities(new SimpleGrantedAuthority(PigguRole.TITULAR.authority())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"transacao\":\"" + transacao + "\"}"));
    }

    private ResultActions avisar(String signedPayload) throws Exception {
        return mockMvc.perform(post("/api/billing/webhooks/app-store")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"signedPayload\":\"" + signedPayload + "\"}"));
    }
}
