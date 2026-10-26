package com.piggu.identity.billing;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.common.security.Plano;
import com.piggu.identity.avisos.CanalDeEmail;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private CanalDeEmail email;

    private UserAccount conta;
    private UserAccount membro;

    @BeforeEach
    void preparar() {
        usuarios.deleteAll();
        jdbc.update("DELETE FROM trial_campaigns");
        jdbc.update("DELETE FROM trial_cpf_history");
        jdbc.update("DELETE FROM billing_events");
        Household familia = familias.save(new Household("Familia de teste"));
        UserAccount nova = new UserAccount("titular@piggu.test", PigguRole.TITULAR, familia.getId());
        nova.confirmarEmail();
        conta = usuarios.save(nova);
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
    @DisplayName("teste gratis: a conta tem 14 dias ate a Stripe avisar que o teste comecou; depois, nunca mais")
    void testeGratisUmaVez() {
        assertThat(assinaturas.plano(conta.getId()).diasDeTeste()).isEqualTo(14);

        receber(evento("customer.subscription.created", "trialing", Instant.now().plus(Duration.ofDays(14)), Instant.now()));

        assertThat(planoDe(conta)).as("em teste ja e Premium").isEqualTo(Plano.PREMIUM);
        assertThat(assinaturas.plano(conta.getId()).diasDeTeste()).isZero();
    }

    @Test
    @DisplayName("CPF de conta excluida que ja testou: a conta nova com o mesmo CPF fica sem teste")
    void cpfDeContaExcluida() {
        jdbc.update("INSERT INTO trial_cpf_history (cpf_indice, used_at) VALUES ('indice-excluido', now())");
        UserAccount nova = usuarios.findById(conta.getId()).orElseThrow();
        nova.informarCpf("cifrado", "indice-excluido");
        usuarios.save(nova);

        assertThat(assinaturas.plano(conta.getId()).diasDeTeste()).isZero();
    }

    @Test
    @DisplayName("campanha para quem ja testou: libera o teste de novo, uma vez por conta")
    void campanhaLiberaDeNovo() {
        campanha("VOLTA30", 30, true, Instant.now().plus(Duration.ofDays(10)));
        receber(evento("customer.subscription.created", "trialing", Instant.now().plus(Duration.ofDays(14)),
                Instant.now().minusSeconds(60)));
        receber(evento("customer.subscription.deleted", "canceled", Instant.now(), Instant.now()));

        assertThat(assinaturas.plano(conta.getId()).diasDeTeste()).as("ja testou").isZero();
        assertThat(assinaturas.plano(conta.getId(), " volta30 ").diasDeTeste()).as("o link da campanha").isEqualTo(30);

        // A Stripe avisa o teste da campanha: o uso fica registrado e a campanha nao vale de novo.
        receber(evento("customer.subscription.created", "trialing", Instant.now().plus(Duration.ofDays(30)),
                Instant.now().plusSeconds(1)).replace("\"metadata\":{", "\"metadata\":{\"campanha\":\"VOLTA30\","));
        receber(evento("customer.subscription.deleted", "canceled", Instant.now(), Instant.now().plusSeconds(2)));

        assertThat(jdbc.queryForObject("SELECT uses FROM trial_campaigns WHERE code = 'VOLTA30'", Integer.class)).isEqualTo(1);
        assertThat(assinaturas.plano(conta.getId(), "VOLTA30").diasDeTeste()).isZero();
        assertThatThrownBy(() -> assinaturas.checkout(conta.getId(), Periodo.MENSAL, "VOLTA30"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("já usou esta promoção");
    }

    @Test
    @DisplayName("campanha so para quem nunca testou, vencida ou lotada nao abre o pagamento")
    void campanhaInvalida() {
        campanha("NOVOS", 21, false, Instant.now().plus(Duration.ofDays(10)));
        campanha("ACABOU", 21, true, Instant.now().minusSeconds(1));
        jdbc.update("INSERT INTO trial_campaigns (code, trial_days, starts_at, ends_at, max_uses, uses) "
                + "VALUES ('LOTADA', 21, now() - interval '1 day', now() + interval '1 day', 1, 1)");

        assertThat(assinaturas.plano(conta.getId(), "NOVOS").diasDeTeste()).as("nunca testou").isEqualTo(21);
        receber(evento("customer.subscription.created", "trialing", Instant.now().plus(Duration.ofDays(14)),
                Instant.now().minusSeconds(60)));
        receber(evento("customer.subscription.deleted", "canceled", Instant.now(), Instant.now()));

        assertThatThrownBy(() -> assinaturas.checkout(conta.getId(), Periodo.MENSAL, "NOVOS"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ainda não usou o teste");
        assertThatThrownBy(() -> assinaturas.checkout(conta.getId(), Periodo.MENSAL, "ACABOU"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("não existe ou já acabou");
        assertThatThrownBy(() -> assinaturas.checkout(conta.getId(), Periodo.MENSAL, "LOTADA"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("não existe ou já acabou");
    }

    @Test
    @DisplayName("3 dias antes do fim do teste vai um e-mail, e o aviso repetido da Stripe nao manda outro")
    void emailAntesDoFimDoTeste() {
        when(email.ligado()).thenReturn(true);
        when(email.enviar(anyString(), anyString(), anyString())).thenReturn(true);
        Instant fim = Instant.now().plus(Duration.ofDays(3));
        String aviso = evento("customer.subscription.trial_will_end", "trialing", fim, Instant.now())
                .replace("\"start_date\"", "\"trial_end\":" + fim.getEpochSecond() + ",\"start_date\"");

        receber(aviso);
        receber(aviso);
        receber(evento("customer.subscription.updated", "trialing", fim, Instant.now()));

        verify(email, times(1)).enviar(eq("titular@piggu.test"), contains("termina em"), contains("Gerenciar assinatura"));
    }

    private void campanha(String codigo, int dias, boolean paraQuemJaTestou, Instant fim) {
        jdbc.update("INSERT INTO trial_campaigns (code, trial_days, starts_at, ends_at, for_previous_trial_users) "
                + "VALUES (?, ?, now() - interval '1 day', ?, ?)", codigo, dias, java.sql.Timestamp.from(fim), paraQuemJaTestou);
    }

    @Test
    @DisplayName("arrependimento conta da primeira cobranca: no teste, ate 7 dias depois do fim dele")
    void reembolsoDaPrimeiraCobranca() {
        Instant agora = Instant.now();
        Instant fimDoTeste = agora.plus(Duration.ofDays(14));
        receber(evento("customer.subscription.created", "trialing", fimDoTeste, agora.minusSeconds(10)));

        assertThat(assinaturas.plano(conta.getId()).reembolsoAte())
                .isEqualTo(Instant.ofEpochSecond(fimDoTeste.getEpochSecond()).plus(Duration.ofDays(7)));

        // Fim do teste, primeira cobranca paga: a Stripe manda a assinatura ativa com trial_end.
        Instant cobranca = agora;
        receber(evento("customer.subscription.updated", "active", agora.plus(Duration.ofDays(29)), agora)
                .replace("\"start_date\"", "\"trial_end\":" + cobranca.getEpochSecond() + ",\"start_date\""));

        assertThat(assinaturas.plano(conta.getId()).reembolsoAte())
                .isEqualTo(Instant.ofEpochSecond(cobranca.getEpochSecond()).plus(Duration.ofDays(7)));
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
