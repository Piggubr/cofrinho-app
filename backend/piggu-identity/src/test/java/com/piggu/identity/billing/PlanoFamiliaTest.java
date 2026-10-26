package com.piggu.identity.billing;

import com.piggu.common.error.BusinessException;
import com.piggu.common.security.PigguRole;
import com.piggu.common.security.Plano;
import com.piggu.identity.config.StripeProperties;
import com.piggu.identity.domain.Household;
import com.piggu.identity.domain.UserAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Individual libera so quem assinou; casal, o titular e o parceiro; familia, a casa. Sem
 * banco: regra e Stripe simulada.
 */
class PlanoFamiliaTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
    private final UserAccount conta = new UserAccount("titular@piggu.test", PigguRole.TITULAR, UUID.randomUUID());

    private StripePagamentos stripe(String familiaMensal, String familiaAnual) {
        return stripe(familiaMensal, familiaAnual, null, null);
    }

    private StripePagamentos stripe(String familiaMensal, String familiaAnual, String casalMensal, String casalAnual) {
        return new StripePagamentos(builder,
                new StripeProperties("https://stripe.test", "sk_test_x", "whsec_x", "price_m", "price_a", null,
                        familiaMensal, familiaAnual, casalMensal, casalAnual),
                new ObjectMapper(), Clock.systemUTC());
    }

    @Test
    @DisplayName("individual: so quem assinou e Premium; familia: todos da casa")
    void quemEPremium() {
        UUID assinante = UUID.randomUUID();
        UUID outra = UUID.randomUUID();
        Household casa = new Household("Casa");
        Instant agora = Instant.now();

        casa.aplicarAssinatura("WEB", agora.plus(Duration.ofDays(30)), agora, agora, false, assinante);
        assertThat(casa.planoPara(assinante)).isEqualTo(Plano.PREMIUM);
        assertThat(casa.planoPara(outra)).isEqualTo(Plano.GRATUITO);

        casa.aplicarAssinatura("WEB", agora.plus(Duration.ofDays(30)), agora.plusSeconds(1), agora, true, assinante);
        assertThat(casa.planoPara(outra)).isEqualTo(Plano.PREMIUM);

        casa.aplicarAssinatura("WEB", null, agora.plusSeconds(2));
        assertThat(casa.planoPara(assinante)).as("cancelado: ninguem").isEqualTo(Plano.GRATUITO);
    }

    @Test
    @DisplayName("o fim da assinatura de quem nao paga mais a casa nao derruba a de quem paga")
    void fimDeOutraAssinatura() {
        UUID bia = UUID.randomUUID();
        UUID ana = UUID.randomUUID();
        Household casa = new Household("Casa");
        Instant agora = Instant.now();
        casa.aplicarAssinatura("WEB", agora.plus(Duration.ofDays(300)), agora, agora, TipoDePlano.FAMILIA, bia);

        assertThat(casa.aplicarAssinatura("WEB", null, agora.plusSeconds(1), null, TipoDePlano.INDIVIDUAL, ana)).isFalse();

        assertThat(casa.isFamilia()).isTrue();
        assertThat(casa.getSubscriberId()).isEqualTo(bia);
        assertThat(casa.planoVigente()).isEqualTo(Plano.PREMIUM);
    }

    @Test
    @DisplayName("casal: quem assinou e o parceiro sao Premium; o membro, nao")
    void casal() {
        Household casa = new Household("Casa");
        UserAccount parceiro = new UserAccount("parceiro@piggu.test", PigguRole.PARCEIRO, casa.getId());
        UserAccount membro = new UserAccount("filho@piggu.test", PigguRole.MEMBRO, casa.getId());
        Instant agora = Instant.now();

        casa.aplicarAssinatura("WEB", agora.plus(Duration.ofDays(30)), agora, agora, TipoDePlano.CASAL, conta.getId());

        assertThat(casa.isCasal()).isTrue();
        assertThat(casa.planoPara(conta)).isEqualTo(Plano.PREMIUM);
        assertThat(casa.planoPara(parceiro)).isEqualTo(Plano.PREMIUM);
        assertThat(casa.planoPara(membro)).isEqualTo(Plano.GRATUITO);
    }

    @Test
    @DisplayName("checkout do casal cobra o preco do casal; sem preco, e recusado")
    void checkoutCasal() {
        servidor.expect(requestTo("https://stripe.test/v1/checkout/sessions"))
                .andExpect(content().string(containsString("line_items%5B0%5D%5Bprice%5D=price_cm")))
                .andRespond(withSuccess("{\"url\":\"https://checkout.stripe.test/s\"}", MediaType.APPLICATION_JSON));

        StripePagamentos comCasal = stripe(null, null, "price_cm", "price_ca");
        assertThat(comCasal.casalHabilitado()).isTrue();
        comCasal.abrirCheckout(conta, Periodo.CASAL_MENSAL, 0);
        servidor.verify();

        assertThatThrownBy(() -> stripe(null, null).abrirCheckout(conta, Periodo.CASAL_ANUAL, 0))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("o aviso da Stripe e o produto da loja dizem casal")
    void avisoDoCasal() {
        StripePagamentos pagamentos = stripe("price_fm", "price_fa", "price_cm", "price_ca");

        assertThat(aviso(pagamentos, "price_ca").tipo()).isEqualTo(TipoDePlano.CASAL);
        assertThat(aviso(pagamentos, "price_fm").tipo()).isEqualTo(TipoDePlano.FAMILIA);
        assertThat(aviso(pagamentos, "price_m").tipo()).isEqualTo(TipoDePlano.INDIVIDUAL);
        assertThat(RevenueCatLoja.tipoDoProduto("piggu_casal_anual")).isEqualTo(TipoDePlano.CASAL);
        assertThat(RevenueCatLoja.tipoDoProduto("piggu_familia_mensal")).isEqualTo(TipoDePlano.FAMILIA);
        assertThat(RevenueCatLoja.tipoDoProduto("piggu_premium_mensal")).isEqualTo(TipoDePlano.INDIVIDUAL);
    }

    @Test
    @DisplayName("checkout do familia cobra o preco do familia")
    void checkoutFamilia() {
        servidor.expect(requestTo("https://stripe.test/v1/checkout/sessions"))
                .andExpect(content().string(containsString("line_items%5B0%5D%5Bprice%5D=price_fa")))
                .andRespond(withSuccess("{\"url\":\"https://checkout.stripe.test/s\"}", MediaType.APPLICATION_JSON));

        stripe("price_fm", "price_fa").abrirCheckout(conta, Periodo.FAMILIA_ANUAL, 0);
        servidor.verify();
    }

    @Test
    @DisplayName("sem preco do familia configurado, o checkout do familia e recusado")
    void familiaDesligado() {
        StripePagamentos semFamilia = stripe(null, null);

        assertThat(semFamilia.familiaHabilitado()).isFalse();
        assertThatThrownBy(() -> semFamilia.abrirCheckout(conta, Periodo.FAMILIA_MENSAL, 0))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("o aviso da Stripe diz familia pelo preco cobrado")
    void avisoPeloPreco() {
        StripePagamentos pagamentos = stripe("price_fm", "price_fa");

        assertThat(aviso(pagamentos, "price_fm").familia()).isTrue();
        assertThat(aviso(pagamentos, "price_m").familia()).isFalse();
    }

    private EventoDeAssinatura aviso(StripePagamentos pagamentos, String preco) {
        long agora = Instant.now().getEpochSecond();
        String corpo = """
                {"id":"evt_1","type":"customer.subscription.updated","created":%d,"data":{"object":{
                  "id":"sub_1","customer":"cus_1","status":"active","metadata":{"userId":"%s"},
                  "items":{"data":[{"current_period_end":%d,"price":{"id":"%s"}}]}}}}
                """.formatted(agora, conta.getId(), agora + 86400, preco);
        return pagamentos.lerWebhook(corpo, "t=" + agora + ",v1=" + pagamentos.hmacHex(agora + "." + corpo)).orElseThrow();
    }
}
