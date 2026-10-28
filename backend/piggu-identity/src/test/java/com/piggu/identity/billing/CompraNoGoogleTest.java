package com.piggu.identity.billing;

import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.piggu.common.dados.Consentimentos;
import com.piggu.common.error.BusinessException;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Compra no Android pelo Google Play: token do app e avisos em tempo real pelo push do Pub/Sub. */
@AutoConfigureMockMvc
class CompraNoGoogleTest extends PostgresIntegrationTest {

    private static final GoogleDeTeste GOOGLE = new GoogleDeTeste();
    private static final String ATIVA = "SUBSCRIPTION_STATE_ACTIVE";
    private static final String EXPIRADA = "SUBSCRIPTION_STATE_EXPIRED";
    private static final int RENOVOU = 2;
    private static final int CANCELOU = 3;
    private static final int EXPIROU = 13;

    @DynamicPropertySource
    static void google(DynamicPropertyRegistry propriedades) {
        propriedades.add("piggu.google.jwk-set-uri", GOOGLE::jwks);
        propriedades.add("piggu.google-play.push-audiencia", () -> GoogleDeTeste.AUDIENCIA);
        propriedades.add("piggu.google-play.push-email", () -> GoogleDeTeste.CONTA_DO_PUSH);
    }

    @TestConfiguration
    static class GoogleFalso {
        @Bean
        @Primary
        GooglePlayApi googlePlayDeTeste() {
            return GOOGLE;
        }
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
        GOOGLE.limpar();
        conta = familias.criarConta("google@piggu.test", "Google", Consentimentos.VERSAO_DO_AVISO);
    }

    @Test
    @DisplayName("o token que o app manda depois da compra vira Premium da conta, pelo Google Play, e a compra e confirmada")
    void compraNoApp() throws Exception {
        Instant vence = Instant.now().plus(Duration.ofDays(30)).truncatedTo(ChronoUnit.MILLIS);
        GOOGLE.assinatura("tok-1", conta.getId(), "premium_mensal", ATIVA, vence);

        comprar("premium_mensal", "tok-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plano").value("PREMIUM"))
                .andExpect(jsonPath("$.origem").value("PLAY_STORE"))
                .andExpect(jsonPath("$.voceAssinou").value(true));

        assertThat(assinaturas.plano(conta.getId(), null).premiumAte()).isEqualTo(vence);
        assertThat(GOOGLE.confirmadas).containsExactly("tok-1");

        // Restaurar a mesma compra nao confirma de novo: o Google ja tem o acknowledge.
        comprar("premium_mensal", "tok-1").andExpect(status().isOk());
        assertThat(GOOGLE.confirmadas).containsExactly("tok-1");
    }

    @Test
    @DisplayName("token que o Google nao conhece, de outro produto ou de assinatura que nao esta ativa e recusado")
    void tokenInvalido() throws Exception {
        comprar("premium_mensal", "nao-existe").andExpect(status().isUnprocessableContent());

        GOOGLE.assinatura("moedas", conta.getId(), "moedas_100", ATIVA, Instant.now().plus(Duration.ofDays(30)));
        comprar("premium_mensal", "moedas").andExpect(status().isUnprocessableContent());

        GOOGLE.assinatura("pendente", conta.getId(), "premium_anual", "SUBSCRIPTION_STATE_PENDING", Instant.now().plus(Duration.ofDays(365)));
        comprar("premium_anual", "pendente").andExpect(status().isUnprocessableContent());

        comprar("premium_semanal", "pendente").andExpect(status().isBadRequest());
        assertThat(plano()).isEqualTo(Plano.GRATUITO);
        assertThat(GOOGLE.confirmadas).isEmpty();
    }

    @Test
    @DisplayName("a compra de outra conta do Piggu nao da Premium para esta nem e confirmada por ela")
    void compraDeOutraConta() throws Exception {
        GOOGLE.assinatura("outra", UUID.randomUUID(), "premium_mensal", ATIVA, Instant.now().plus(Duration.ofDays(30)));
        comprar("premium_mensal", "outra")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("OUTRA_CONTA"));

        GOOGLE.assinatura("sem-conta", null, "premium_mensal", ATIVA, Instant.now().plus(Duration.ofDays(30)));
        comprar("premium_mensal", "sem-conta").andExpect(status().isConflict());

        assertThat(plano()).isEqualTo(Plano.GRATUITO);
        assertThat(GOOGLE.confirmadas).isEmpty();
    }

    @Test
    @DisplayName("se o acknowledge falha a compra vale igual, e o proximo aviso confirma")
    void confirmacaoQueFalha() throws Exception {
        GOOGLE.assinatura("tok-2", conta.getId(), "premium_anual", ATIVA, Instant.now().plus(Duration.ofDays(365)));
        GOOGLE.confirmacaoFalha = true;
        comprar("premium_anual", "tok-2").andExpect(status().isOk()).andExpect(jsonPath("$.plano").value("PREMIUM"));
        assertThat(GOOGLE.confirmadas).isEmpty();

        GOOGLE.confirmacaoFalha = false;
        avisar(GoogleDeTeste.avisoDeAssinatura(4, "tok-2")).andExpect(status().isOk());
        assertThat(GOOGLE.confirmadas).containsExactly("tok-2");
    }

    @Test
    @DisplayName("aviso de renovacao estende; o estado vem do Google, nao do aviso")
    void renovacao() throws Exception {
        GOOGLE.assinatura("tok-3", conta.getId(), "premium_mensal", ATIVA, Instant.now().plus(Duration.ofDays(2)));
        avisar(GoogleDeTeste.avisoDeAssinatura(4, "tok-3")).andExpect(status().isOk());
        assertThat(plano()).isEqualTo(Plano.PREMIUM);

        Instant renovado = Instant.now().plus(Duration.ofDays(32)).truncatedTo(ChronoUnit.MILLIS);
        GOOGLE.assinaturas.get("tok-3").getLineItems().getFirst().setExpiryTime(renovado.toString());
        avisar(GoogleDeTeste.avisoDeAssinatura(RENOVOU, "tok-3")).andExpect(status().isOk());
        assertThat(assinaturas.plano(conta.getId(), null).premiumAte()).isEqualTo(renovado);
    }

    @Test
    @DisplayName("cancelar so desliga a renovacao; expirar ou revogar encerra")
    void cancelamentoEExpiracao() throws Exception {
        GOOGLE.assinatura("tok-4", conta.getId(), "premium_mensal", "SUBSCRIPTION_STATE_CANCELED", Instant.now().plus(Duration.ofDays(10)));
        avisar(GoogleDeTeste.avisoDeAssinatura(CANCELOU, "tok-4")).andExpect(status().isOk());
        assertThat(plano()).isEqualTo(Plano.PREMIUM);

        GOOGLE.assinaturas.get("tok-4").setSubscriptionState(EXPIRADA);
        avisar(GoogleDeTeste.avisoDeAssinatura(EXPIROU, "tok-4")).andExpect(status().isOk());
        assertThat(plano()).isEqualTo(Plano.GRATUITO);

        // Reembolso chega como compra anulada de assinatura (productType 1).
        GOOGLE.assinatura("tok-5", conta.getId(), "premium_anual", ATIVA, Instant.now().plus(Duration.ofDays(365)));
        avisar(GoogleDeTeste.avisoDeAssinatura(4, "tok-5"));
        assertThat(plano()).isEqualTo(Plano.PREMIUM);
        GOOGLE.assinaturas.get("tok-5").setSubscriptionState(EXPIRADA);
        avisar(Map.of("version", "1.0", "packageName", "br.com.piggu.app", "eventTimeMillis", "1",
                "voidedPurchaseNotification", Map.of("purchaseToken", "tok-5", "orderId", "GPA.1", "productType", 1)))
                .andExpect(status().isOk());
        assertThat(plano()).isEqualTo(Plano.GRATUITO);
    }

    @Test
    @DisplayName("na troca de mensal para anual, o fim do token antigo nao derruba o novo")
    void trocaDePlano() throws Exception {
        Instant anual = Instant.now().plus(Duration.ofDays(365)).truncatedTo(ChronoUnit.MILLIS);
        GOOGLE.assinatura("mensal", conta.getId(), "premium_mensal", ATIVA, Instant.now().plus(Duration.ofDays(20)));
        GOOGLE.assinatura("anual", conta.getId(), "premium_anual", ATIVA, anual);
        comprar("premium_mensal", "mensal");
        comprar("premium_anual", "anual");

        // O Google encerra o token antigo na hora da troca.
        GOOGLE.assinaturas.get("mensal").setSubscriptionState(EXPIRADA)
                .getLineItems().getFirst().setExpiryTime(Instant.now().toString());
        avisar(GoogleDeTeste.avisoDeAssinatura(EXPIROU, "mensal")).andExpect(status().isOk());

        assertThat(plano()).isEqualTo(Plano.PREMIUM);
        assertThat(assinaturas.plano(conta.getId(), null).premiumAte()).isEqualTo(anual);
    }

    @Test
    @DisplayName("quem ja e Premium pelo site continua pelo site: o Google nao troca nem derruba")
    void premiumPeloSite() throws Exception {
        Household casa = familias.daConta(conta);
        casa.aplicarAssinatura("WEB", Instant.now().plus(Duration.ofDays(30)), Instant.now().minusSeconds(120), null,
                TipoDePlano.INDIVIDUAL, conta.getId());
        casas.save(casa);

        GOOGLE.assinatura("tok-6", conta.getId(), "premium_anual", ATIVA, Instant.now().plus(Duration.ofDays(365)));
        comprar("premium_anual", "tok-6").andExpect(status().isOk()).andExpect(jsonPath("$.origem").value("WEB"));
        GOOGLE.assinaturas.get("tok-6").setSubscriptionState(EXPIRADA);
        avisar(GoogleDeTeste.avisoDeAssinatura(EXPIROU, "tok-6")).andExpect(status().isOk());

        assertThat(plano()).isEqualTo(Plano.PREMIUM);
        assertThat(assinaturas.plano(conta.getId(), null).origem()).isEqualTo("WEB");
    }

    @Test
    @DisplayName("push sem o token do Google e 401; de outra chave, 401; para outra URL ou de outra conta, 403")
    void pushSemProva() throws Exception {
        GOOGLE.assinatura("tok-7", conta.getId(), "premium_mensal", ATIVA, Instant.now().plus(Duration.ofDays(30)));
        String corpo = GoogleDeTeste.push(GoogleDeTeste.avisoDeAssinatura(4, "tok-7"));

        avisar(corpo, null).andExpect(status().isUnauthorized());
        avisar(corpo, "nao-e-um-jwt").andExpect(status().isUnauthorized());
        String outraChave = GoogleDeTeste.token(new RSAKeyGenerator(2048).keyID("google-teste").generate(),
                GoogleDeTeste.AUDIENCIA, GoogleDeTeste.CONTA_DO_PUSH);
        avisar(corpo, outraChave).andExpect(status().isUnauthorized());
        avisar(corpo, GOOGLE.token("https://outro.test/webhook", GoogleDeTeste.CONTA_DO_PUSH)).andExpect(status().isForbidden());
        avisar(corpo, GOOGLE.token(GoogleDeTeste.AUDIENCIA, "intruso@outro.iam.gserviceaccount.com")).andExpect(status().isForbidden());

        assertThat(plano()).isEqualTo(Plano.GRATUITO);
    }

    @Test
    @DisplayName("o aviso de teste do Play Console e a compra unica sao aceitos sem mudar nada")
    void avisoDeTeste() throws Exception {
        avisar(Map.of("version", "1.0", "packageName", "br.com.piggu.app", "eventTimeMillis", "1",
                "testNotification", Map.of("version", "1.0"))).andExpect(status().isOk());
        avisar(Map.of("version", "1.0", "packageName", "br.com.piggu.app", "eventTimeMillis", "1",
                "oneTimeProductNotification", Map.of("version", "1.0", "notificationType", 1, "purchaseToken", "x", "sku", "y")))
                .andExpect(status().isOk());
        avisar(GoogleDeTeste.avisoDeAssinatura(4, "desconhecido")).andExpect(status().isOk());
        assertThat(plano()).isEqualTo(Plano.GRATUITO);
    }

    @Test
    @DisplayName("sem a conta de servico, a loja do Google responde 501 e o identity sobe igual")
    void semConfiguracao() {
        GooglePlayApi desligada = new PlayDeveloperApi("br.com.piggu.app", "", null);
        GooglePlayApi ilegivel = new PlayDeveloperApi("br.com.piggu.app", "{nao e json", null);
        assertThat(ilegivel.configurada()).isFalse();

        GooglePlayLoja loja = new GooglePlayLoja(desligada, null, null, GoogleDeTeste.AUDIENCIA, GoogleDeTeste.CONTA_DO_PUSH);
        assertThatThrownBy(() -> loja.lerCompra("tok"))
                .isInstanceOfSatisfying(BusinessException.class, erro -> assertThat(erro.getStatus()).isEqualTo(HttpStatus.NOT_IMPLEMENTED));
        assertThatThrownBy(() -> loja.lerAviso("Bearer x", "e30="))
                .isInstanceOfSatisfying(BusinessException.class, erro -> assertThat(erro.getStatus()).isEqualTo(HttpStatus.NOT_IMPLEMENTED));
    }

    private Plano plano() {
        return assinaturas.plano(conta.getId(), null).plano();
    }

    private ResultActions comprar(String produto, String token) throws Exception {
        return mockMvc.perform(post("/api/billing/google-play/purchases")
                .with(jwt().jwt(jwt -> jwt.subject(conta.getId().toString())
                                .claim("email", conta.getEmail())
                                .claim("role", PigguRole.TITULAR.name())
                                .claim("familia", conta.getHouseholdId().toString()))
                        .authorities(new SimpleGrantedAuthority(PigguRole.TITULAR.authority())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"produto\":\"" + produto + "\",\"purchaseToken\":\"" + token + "\"}"));
    }

    private ResultActions avisar(Map<String, Object> aviso) throws Exception {
        return avisar(GoogleDeTeste.push(aviso), GOOGLE.token(GoogleDeTeste.AUDIENCIA, GoogleDeTeste.CONTA_DO_PUSH));
    }

    private ResultActions avisar(String corpo, String token) throws Exception {
        var pedido = post("/api/billing/webhooks/google-play").contentType(MediaType.APPLICATION_JSON).content(corpo);
        if (token != null) {
            pedido.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(pedido);
    }
}
