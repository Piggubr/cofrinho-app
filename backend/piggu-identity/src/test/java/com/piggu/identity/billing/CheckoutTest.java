package com.piggu.identity.billing;

import com.piggu.common.security.PigguRole;
import com.piggu.identity.config.StripeProperties;
import com.piggu.identity.domain.UserAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** O teste gratis vai no Checkout so quando a conta ainda tem direito a ele. */
class CheckoutTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer stripe = MockRestServiceServer.bindTo(builder).build();
    private final StripePagamentos pagamentos = new StripePagamentos(builder,
            new StripeProperties("https://stripe.test", "sk_test_x", "whsec_x", "price_m", "price_a", null),
            new ObjectMapper(), Clock.systemUTC());
    private final UserAccount conta = new UserAccount("titular@piggu.test", PigguRole.TITULAR, UUID.randomUUID());

    @Test
    @DisplayName("com dias de teste, o Checkout leva trial_period_days")
    void comTeste() {
        stripe.expect(requestTo("https://stripe.test/v1/checkout/sessions"))
                .andExpect(content().string(containsString("subscription_data%5Btrial_period_days%5D=7")))
                .andRespond(withSuccess("{\"url\":\"https://checkout.stripe.test/s\"}", MediaType.APPLICATION_JSON));

        assertThat(pagamentos.abrirCheckout(conta, Periodo.MENSAL, 7)).isEqualTo("https://checkout.stripe.test/s");
        stripe.verify();
    }

    @Test
    @DisplayName("sem dias de teste, cobra na hora")
    void semTeste() {
        stripe.expect(requestTo("https://stripe.test/v1/checkout/sessions"))
                .andExpect(content().string(not(containsString("trial_period_days"))))
                .andRespond(withSuccess("{\"url\":\"https://checkout.stripe.test/s\"}", MediaType.APPLICATION_JSON));

        pagamentos.abrirCheckout(conta, Periodo.ANUAL, 0);
        stripe.verify();
    }
}
