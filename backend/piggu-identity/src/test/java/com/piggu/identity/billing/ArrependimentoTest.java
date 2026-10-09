package com.piggu.identity.billing;

import com.piggu.common.error.BusinessException;
import com.piggu.identity.config.StripeProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Arrependimento de 7 dias (CDC art. 49) contra a API da Stripe simulada. */
class ArrependimentoTest {

    private static final Instant AGORA = Instant.parse("2026-10-08T12:00:00Z");
    private static final String STRIPE = "https://stripe.test";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer stripe = MockRestServiceServer.bindTo(builder).build();
    private final StripePagamentos pagamentos = new StripePagamentos(builder,
            new StripeProperties(STRIPE, "sk_test_x", "whsec_x", "price_m", "price_a", null),
            new ObjectMapper(), Clock.fixed(AGORA, ZoneOffset.UTC));

    @Test
    @DisplayName("dentro dos 7 dias: devolve as cobrancas desta assinatura e cancela na hora")
    void dentroDoPrazo() {
        long inicio = AGORA.minus(Duration.ofDays(2)).getEpochSecond();
        assinatura(inicio);
        stripe.expect(requestTo(STRIPE + "/v1/charges?customer=cus_1&limit=20"))
                .andRespond(json("""
                        {"data":[
                          {"id":"ch_novo","created":%d,"paid":true,"refunded":false},
                          {"id":"ch_antigo","created":%d,"paid":true,"refunded":false},
                          {"id":"ch_devolvido","created":%d,"paid":true,"refunded":true}
                        ]}""".formatted(inicio + 10, inicio - 86400, inicio + 20)));
        stripe.expect(requestTo(STRIPE + "/v1/refunds"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string("charge=ch_novo&reason=requested_by_customer"))
                .andRespond(json("{\"id\":\"re_1\"}"));
        stripe.expect(requestTo(STRIPE + "/v1/subscriptions/sub_1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(json("{\"id\":\"sub_1\",\"status\":\"canceled\"}"));

        pagamentos.cancelarComReembolso("cus_1", AssinaturaService.PRAZO_DE_ARREPENDIMENTO);

        stripe.verify();
    }

    @Test
    @DisplayName("depois dos 7 dias nada e devolvido nem cancelado por aqui")
    void foraDoPrazo() {
        assinatura(AGORA.minus(Duration.ofDays(8)).getEpochSecond());

        assertThatThrownBy(() -> pagamentos.cancelarComReembolso("cus_1", AssinaturaService.PRAZO_DE_ARREPENDIMENTO))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> org.assertj.core.api.Assertions.assertThat(erro.getCodigo()).isEqualTo("PRAZO_DE_REEMBOLSO"));
        stripe.verify();
    }

    private void assinatura(long inicio) {
        stripe.expect(requestTo(STRIPE + "/v1/subscriptions?customer=cus_1&status=all&limit=10"))
                .andRespond(json("""
                        {"data":[{"id":"sub_1","status":"active","start_date":%d}]}""".formatted(inicio)));
    }

    private static org.springframework.test.web.client.ResponseCreator json(String corpo) {
        return withSuccess(corpo, MediaType.APPLICATION_JSON);
    }
}
