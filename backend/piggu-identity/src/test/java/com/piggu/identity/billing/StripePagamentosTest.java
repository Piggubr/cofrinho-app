package com.piggu.identity.billing;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.error.UpstreamException;
import com.piggu.common.security.PigguRole;
import com.piggu.identity.config.StripeProperties;
import com.piggu.identity.domain.UserAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Portal, encerramento do cliente, avisos da Stripe e as falhas da API, contra a Stripe simulada. */
class StripePagamentosTest {

    private static final Instant AGORA = Instant.parse("2026-10-08T12:00:00Z");
    private static final String STRIPE = "https://stripe.test";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer stripe = MockRestServiceServer.bindTo(builder).build();
    private final StripePagamentos pagamentos = new StripePagamentos(builder,
            new StripeProperties(STRIPE, "sk_test_x", "whsec_x", "price_m", "price_a", "https://piggu.test",
                    null, null, null, null),
            new ObjectMapper(), Clock.fixed(AGORA, ZoneOffset.UTC));
    private final StripePagamentos desligado = new StripePagamentos(RestClient.builder(),
            new StripeProperties(STRIPE, null, null, null, null, null, null, null, null, null),
            new ObjectMapper(), Clock.fixed(AGORA, ZoneOffset.UTC));
    private final UserAccount conta = new UserAccount("titular@piggu.test", PigguRole.TITULAR, UUID.randomUUID());

    @Test
    @DisplayName("quem ja e cliente volta ao Checkout pelo id do cliente, sem repetir o e-mail")
    void checkoutDeQuemJaECliente() {
        conta.lembrarClienteNoProvedor("cus_1");
        stripe.expect(requestTo(STRIPE + "/v1/checkout/sessions"))
                .andExpect(content().string(allOf(containsString("customer=cus_1"), not(containsString("customer_email")))))
                .andRespond(json("{\"url\":\"https://checkout.stripe.test/s\"}"));

        assertThat(pagamentos.abrirCheckout(conta, Periodo.MENSAL, 0)).isEqualTo("https://checkout.stripe.test/s");
        stripe.verify();
    }

    @Test
    @DisplayName("o portal abre so para quem ja assinou pelo site e devolve para a tela de planos")
    void portal() {
        assertThatThrownBy(() -> pagamentos.abrirPortal(conta)).isInstanceOf(BusinessException.class);

        conta.lembrarClienteNoProvedor("cus_1");
        stripe.expect(requestTo(STRIPE + "/v1/billing_portal/sessions"))
                .andExpect(header("Authorization", "Bearer sk_test_x"))
                .andExpect(content().string(containsString("return_url=https%3A%2F%2Fpiggu.test%2Fplano")))
                .andRespond(json("{\"url\":\"https://portal.stripe.test/p\"}"));

        assertThat(pagamentos.abrirPortal(conta)).isEqualTo("https://portal.stripe.test/p");
        stripe.verify();
    }

    @Test
    @DisplayName("Stripe desligada: nada de checkout, consulta ou encerramento")
    void stripeDesligada() {
        assertThat(desligado.habilitado()).isFalse();
        assertThat(desligado.familiaHabilitado()).isFalse();
        assertThat(desligado.casalHabilitado()).isFalse();
        assertThatThrownBy(() -> desligado.abrirCheckout(conta, Periodo.MENSAL, 0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> desligado.cancelarComReembolso("cus_1", Duration.ofDays(7)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> desligado.encerrarCliente("cus_1")).isInstanceOf(UpstreamException.class);
        assertThatThrownBy(() -> desligado.lerWebhook("{}", "t=1,v1=x")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("encerrar cliente que a Stripe ja nao conhece conta como encerrado")
    void encerraCliente() {
        stripe.expect(requestTo(STRIPE + "/v1/customers/cus_1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());
        stripe.expect(requestTo(STRIPE + "/v1/customers/cus_2")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatCode(() -> pagamentos.encerrarCliente("cus_1")).doesNotThrowAnyException();
        assertThatCode(() -> pagamentos.encerrarCliente("cus_2")).doesNotThrowAnyException();
        stripe.verify();
    }

    @Test
    @DisplayName("erro ou falha de rede ao encerrar vira indisponivel")
    void falhaAoEncerrar() {
        stripe.expect(requestTo(STRIPE + "/v1/customers/cus_1")).andRespond(withServerError());
        stripe.expect(requestTo(STRIPE + "/v1/customers/cus_1")).andRespond(semRede());

        assertThatThrownBy(() -> pagamentos.encerrarCliente("cus_1")).isInstanceOf(UpstreamException.class);
        assertThatThrownBy(() -> pagamentos.encerrarCliente("cus_1")).isInstanceOf(UpstreamException.class);
    }

    @Test
    @DisplayName("sem assinatura paga nao ha o que cancelar")
    void semAssinaturaParaCancelar() {
        stripe.expect(requestTo(STRIPE + "/v1/subscriptions?customer=cus_1&status=all&limit=10"))
                .andRespond(json("{\"data\":[{\"id\":\"sub_1\",\"status\":\"canceled\"}]}"));

        assertThatThrownBy(() -> pagamentos.cancelarComReembolso("cus_1", Duration.ofDays(7)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("assinatura ativa");
    }

    @Test
    @DisplayName("falhas da Stripe ao consultar, devolver e cancelar viram indisponivel")
    void falhasDaApi() {
        String assinaturas = STRIPE + "/v1/subscriptions?customer=cus_1&status=all&limit=10";
        stripe.expect(requestTo(assinaturas)).andRespond(withServerError());
        stripe.expect(requestTo(assinaturas)).andRespond(semRede());
        assinaturaSemCobranca();
        stripe.expect(requestTo(STRIPE + "/v1/subscriptions/sub_1")).andRespond(withServerError());
        assinaturaSemCobranca();
        stripe.expect(requestTo(STRIPE + "/v1/subscriptions/sub_1")).andRespond(semRede());
        stripe.expect(requestTo(STRIPE + "/v1/checkout/sessions")).andRespond(withServerError());
        stripe.expect(requestTo(STRIPE + "/v1/checkout/sessions")).andRespond(semRede());

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> pagamentos.cancelarComReembolso("cus_1", Duration.ofDays(7)))
                    .isInstanceOf(UpstreamException.class);
        }
        assertThatThrownBy(() -> pagamentos.abrirCheckout(conta, Periodo.MENSAL, 0)).isInstanceOf(UpstreamException.class);
        assertThatThrownBy(() -> pagamentos.abrirCheckout(conta, Periodo.MENSAL, 0)).isInstanceOf(UpstreamException.class);
        stripe.verify();
    }

    @Test
    @DisplayName("aviso cancelado tira o Premium; aviso de outro tipo ou sem usuario e ignorado")
    void avisos() {
        String cancelada = evento("customer.subscription.deleted", """
                {"customer":"cus_1","status":"canceled","metadata":{"userId":"%s"},"start_date":%d,
                 "current_period_end":%d}""".formatted(conta.getId(), AGORA.getEpochSecond() - 100, AGORA.getEpochSecond()));
        EventoDeAssinatura evento = pagamentos.lerWebhook(cancelada, assinar(cancelada)).orElseThrow();
        assertThat(evento.usuarioId()).isEqualTo(conta.getId());
        assertThat(evento.premiumAte()).isNull();

        String outroTipo = evento("invoice.paid", "{\"metadata\":{\"userId\":\"%s\"}}".formatted(conta.getId()));
        assertThat(pagamentos.lerWebhook(outroTipo, assinar(outroTipo))).isEmpty();

        String semUsuario = evento("customer.subscription.updated", "{\"status\":\"active\"}");
        assertThat(pagamentos.lerWebhook(semUsuario, assinar(semUsuario))).isEmpty();
    }

    @Test
    @DisplayName("fim do teste gratis avisado e fim do periodo lido da assinatura")
    void fimDoTeste() {
        long fimDoTeste = AGORA.getEpochSecond() + 3 * 86400;
        String aviso = evento("customer.subscription.trial_will_end", """
                {"customer":"cus_1","status":"trialing","metadata":{"userId":"%s","campanha":"VOLTA30"},
                 "trial_end":%d,"current_period_end":%d}""".formatted(conta.getId(), fimDoTeste, fimDoTeste));

        EventoDeAssinatura evento = pagamentos.lerWebhook(aviso, assinar(aviso)).orElseThrow();

        assertThat(evento.premiumAte()).isEqualTo(Instant.ofEpochSecond(fimDoTeste));
        assertThat(evento.testeAcabaEm()).isEqualTo(Instant.ofEpochSecond(fimDoTeste));
        assertThat(evento.campanha()).isEqualTo("VOLTA30");
        assertThat(evento.inicio()).isNull();
    }

    @Test
    @DisplayName("renovacao anual recusada (past_due): Premium so ate 7 dias depois do inicio do periodo nao pago")
    void cobrancaRecusada() {
        long inicio = AGORA.getEpochSecond() - 86400;
        String recusada = evento("customer.subscription.updated", """
                {"customer":"cus_1","status":"past_due","metadata":{"userId":"%s"},"start_date":1,
                 "items":{"data":[{"current_period_start":%d,"current_period_end":%d}]}}""".formatted(
                conta.getId(), inicio, inicio + 365 * 86400));

        EventoDeAssinatura evento = pagamentos.lerWebhook(recusada, assinar(recusada)).orElseThrow();

        assertThat(evento.premiumAte()).isEqualTo(Instant.ofEpochSecond(inicio).plus(Duration.ofDays(7)));
    }

    @Test
    @DisplayName("aviso ilegivel e recusado")
    void avisoIlegivel() {
        String corpo = "isto nao e json";
        assertThatThrownBy(() -> pagamentos.lerWebhook(corpo, assinar(corpo))).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("assinatura ausente, malformada, velha ou errada e recusada")
    void assinaturasRecusadas() {
        String corpo = "{}";
        long agora = AGORA.getEpochSecond();
        String certa = pagamentos.hmacHex(agora + "." + corpo);

        for (String cabecalho : new String[] {
                null,
                "v1=" + certa,
                "t=abc,v1=" + certa,
                "t=" + (agora - 600) + ",v1=" + pagamentos.hmacHex((agora - 600) + "." + corpo),
                "t=" + agora + ",v1=outra",
                "t=" + agora + ",lixo,v0=" + certa}) {
            assertThatThrownBy(() -> pagamentos.conferirAssinatura(corpo, cabecalho))
                    .as(String.valueOf(cabecalho))
                    .isInstanceOf(UnauthorizedException.class);
        }
        assertThatCode(() -> pagamentos.conferirAssinatura(corpo, "t=" + agora + ", v1=errada, v1=" + certa))
                .doesNotThrowAnyException();
    }

    private void assinaturaSemCobranca() {
        stripe.expect(requestTo(STRIPE + "/v1/subscriptions?customer=cus_1&status=all&limit=10"))
                .andRespond(json("{\"data\":[{\"id\":\"sub_1\",\"status\":\"trialing\",\"start_date\":1}]}"));
        stripe.expect(requestTo(STRIPE + "/v1/charges?customer=cus_1&limit=20")).andRespond(json("{\"data\":[]}"));
    }

    private String evento(String tipo, String objeto) {
        return "{\"id\":\"evt_1\",\"type\":\"" + tipo + "\",\"created\":" + AGORA.getEpochSecond()
                + ",\"data\":{\"object\":" + objeto + "}}";
    }

    private String assinar(String corpo) {
        long agora = AGORA.getEpochSecond();
        return "t=" + agora + ",v1=" + pagamentos.hmacHex(agora + "." + corpo);
    }

    private static ResponseCreator json(String corpo) {
        return withSuccess(corpo, MediaType.APPLICATION_JSON);
    }

    private static ResponseCreator semRede() {
        return pedido -> {
            throw new IOException("sem rede");
        };
    }
}
