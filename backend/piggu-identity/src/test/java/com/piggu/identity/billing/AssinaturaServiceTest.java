package com.piggu.identity.billing;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.common.security.Plano;
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
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O Premium so muda pelo aviso assinado da Stripe, e aviso atrasado nao desfaz o mais novo.
 */
@TestPropertySource(properties = {
        "piggu.stripe.secret-key=sk_test_x",
        "piggu.stripe.webhook-secret=whsec_teste",
        "piggu.stripe.price-mensal=price_m",
        "piggu.stripe.price-anual=price_a",
        "piggu.stripe.price-familia-mensal=price_fm",
        "piggu.stripe.price-familia-anual=price_fa"
})
class AssinaturaServiceTest extends PostgresIntegrationTest {

    @Autowired
    private AssinaturaService assinaturas;

    @Autowired
    private StripePagamentos stripe;

    @Autowired
    private UserAccountRepository usuarios;

    @Autowired
    private HouseholdRepository familias;

    @Autowired
    private FamiliaService familiaService;

    private UserAccount conta;
    private UserAccount membro;

    @BeforeEach
    void preparar() {
        usuarios.deleteAll();
        Household familia = familias.save(new Household("Familia de teste"));
        conta = usuarios.save(new UserAccount("titular@piggu.test", PigguRole.TITULAR, familia.getId()));
        membro = usuarios.save(new UserAccount("membro@piggu.test", PigguRole.MEMBRO, familia.getId()));
    }

    private Plano planoDe(UserAccount quem) {
        return assinaturas.plano(quem.getId()).plano();
    }

    @Test
    @DisplayName("individual ativo: Premium so para quem assinou, ate o fim do periodo pago")
    void ativaViraPremium() {
        Instant fim = Instant.now().plus(Duration.ofDays(30));

        receber(evento("customer.subscription.created", "active", fim, Instant.now()));

        UserAccount salva = usuarios.findById(conta.getId()).orElseThrow();
        assertThat(planoDe(conta)).isEqualTo(Plano.PREMIUM);
        assertThat(planoDe(membro)).as("no individual o resto da casa segue gratuito").isEqualTo(Plano.GRATUITO);
        assertThat(assinaturas.plano(membro.getId()).reembolsoAte()).as("so quem assinou desiste").isNull();
        assertThat(salva.getStripeCustomerId()).isEqualTo("cus_123");
        assertThat(assinaturas.plano(conta.getId()).premiumAte()).isEqualTo(Instant.ofEpochSecond(fim.getEpochSecond()));
        assertThat(assinaturas.plano(conta.getId()).reembolsoAte())
                .as("assinou agora: dentro dos 7 dias do arrependimento").isNotNull();
    }

    @Test
    @DisplayName("plano familia: Premium para todos da casa; o tipo sai do preco cobrado")
    void familiaLiberaACasa() {
        receber(evento("customer.subscription.created", "active", Instant.now().plus(Duration.ofDays(30)),
                Instant.now(), "price_fm"));

        assertThat(planoDe(conta)).isEqualTo(Plano.PREMIUM);
        assertThat(planoDe(membro)).isEqualTo(Plano.PREMIUM);
        assertThat(assinaturas.plano(membro.getId()).planoFamilia()).isTrue();
        assertThat(assinaturas.plano(membro.getId()).voceAssinou()).isFalse();
    }

    @Test
    @DisplayName("trocar de individual para familia (pelo portal) libera a casa no aviso seguinte")
    void trocaParaFamilia() {
        Instant agora = Instant.now();
        receber(evento("customer.subscription.created", "active", agora.plus(Duration.ofDays(30)), agora.minusSeconds(10)));
        receber(evento("customer.subscription.updated", "active", agora.plus(Duration.ofDays(30)), agora, "price_fa"));

        assertThat(planoDe(membro)).isEqualTo(Plano.PREMIUM);
    }

    @Test
    @DisplayName("uma assinatura por casa: quem nao assinou nao abre outra")
    void umaAssinaturaPorCasa() {
        receber(evento("customer.subscription.created", "active", Instant.now().plus(Duration.ofDays(30)), Instant.now()));

        assertThatThrownBy(() -> assinaturas.checkout(membro.getId(), Periodo.MENSAL))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Já há uma assinatura");
    }

    @Test
    @DisplayName("familia com mais de 5 pessoas nao assina o plano familia")
    void familiaGrandeDemais() {
        for (int i = 0; i < 4; i++) {
            usuarios.save(new UserAccount("extra" + i + "@piggu.test", PigguRole.MEMBRO, conta.getHouseholdId()));
        }

        assertThatThrownBy(() -> assinaturas.checkout(conta.getId(), Periodo.FAMILIA_MENSAL))
                .isInstanceOf(BusinessException.class).hasMessageContaining("até 5 pessoas");
    }

    @Test
    @DisplayName("com o plano familia a casa para de aceitar convite na 5a pessoa (contando convites)")
    void conviteAlemDoFamilia() {
        receber(evento("customer.subscription.created", "active", Instant.now().plus(Duration.ofDays(30)),
                Instant.now(), "price_fm"));
        CurrentUser titular = new CurrentUser(conta.getId(), conta.getEmail(), PigguRole.TITULAR, Plano.PREMIUM,
                conta.getHouseholdId());
        familiaService.convidar(titular, "c1@piggu.test");
        familiaService.convidar(titular, "c2@piggu.test");
        familiaService.convidar(titular, "c3@piggu.test");

        assertThatThrownBy(() -> familiaService.convidar(titular, "c4@piggu.test"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("até 5 pessoas");
    }

    @Test
    @DisplayName("teste gratis: a conta tem 7 dias ate a Stripe avisar que o teste comecou; depois, nunca mais")
    void testeGratisUmaVez() {
        assertThat(assinaturas.plano(conta.getId()).diasDeTeste()).isEqualTo(7);

        receber(evento("customer.subscription.created", "trialing", Instant.now().plus(Duration.ofDays(7)), Instant.now()));

        assertThat(planoDe(conta)).as("em teste ja e Premium").isEqualTo(Plano.PREMIUM);
        assertThat(assinaturas.plano(conta.getId()).diasDeTeste()).isZero();
    }

    @Test
    @DisplayName("cancelada volta ao gratuito, e um aviso antigo chegando depois nao reativa")
    void avisoAtrasadoNaoReativa() {
        Instant agora = Instant.now();
        Instant fim = agora.plus(Duration.ofDays(30));

        receber(evento("customer.subscription.deleted", "canceled", fim, agora));
        receber(evento("customer.subscription.updated", "active", fim, agora.minusSeconds(60)));

        assertThat(planoDe(conta)).isEqualTo(Plano.GRATUITO);
    }

    @Test
    @DisplayName("aviso com assinatura errada ou velha e recusado sem mexer no plano")
    void assinaturaInvalida() {
        String corpo = evento("customer.subscription.created", "active", Instant.now().plusSeconds(3600), Instant.now());
        long agora = Instant.now().getEpochSecond();
        long velho = agora - 600;

        assertThatThrownBy(() -> assinaturas.aplicarWebhook(corpo, "t=" + agora + ",v1=deadbeef"))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> assinaturas.aplicarWebhook(corpo, "t=" + velho + ",v1=" + stripe.hmacHex(velho + "." + corpo)))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> assinaturas.aplicarWebhook(corpo, null))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(planoDe(conta)).isEqualTo(Plano.GRATUITO);
    }

    @Test
    @DisplayName("evento sem dono no Piggu e ignorado sem erro")
    void semDonoIgnorado() {
        receber(evento("customer.subscription.created", "active", Instant.now().plusSeconds(3600), Instant.now())
                .replace(conta.getId().toString(), UUID.randomUUID().toString()));

        assertThat(planoDe(conta)).isEqualTo(Plano.GRATUITO);
    }

    private void receber(String corpo) {
        long agora = Instant.now().getEpochSecond();
        assinaturas.aplicarWebhook(corpo, "t=" + agora + ",v1=" + stripe.hmacHex(agora + "." + corpo));
    }

    private String evento(String tipo, String status, Instant fim, Instant criado) {
        return evento(tipo, status, fim, criado, "price_m");
    }

    /** Formato da API nova: o fim do periodo e o preco moram no item da assinatura. */
    private String evento(String tipo, String status, Instant fim, Instant criado, String preco) {
        return """
                {"id":"evt_1","type":"%s","created":%d,"data":{"object":{
                  "id":"sub_1","customer":"cus_123","status":"%s","metadata":{"userId":"%s"},"start_date":%d,
                  "items":{"data":[{"current_period_end":%d,"price":{"id":"%s"}}]}}}}
                """.formatted(tipo, criado.getEpochSecond(), status, conta.getId(), criado.getEpochSecond(),
                fim.getEpochSecond(), preco);
    }
}
