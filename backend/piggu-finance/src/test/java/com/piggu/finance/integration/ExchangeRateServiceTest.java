package com.piggu.finance.integration;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UpstreamException;
import com.piggu.finance.api.dto.CurrencyResponse;
import com.piggu.finance.api.dto.ExchangeRateResponse;
import com.piggu.finance.config.IntegracoesProperties;
import com.piggu.finance.domain.AppSetting;
import com.piggu.finance.domain.AppSettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Cotacao entre moedas escolhidas pelo usuario, com a fonte de cambio simulada. */
class ExchangeRateServiceTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer fonte = MockRestServiceServer.bindTo(builder).build();
    private final AppSettingRepository configuracoes = mock(AppSettingRepository.class);
    private final ExchangeRateService servico = new ExchangeRateService(builder,
            new IntegracoesProperties(null, new IntegracoesProperties.Cambio("https://cambio.test/v2", null, null)),
            configuracoes);

    @Test
    @DisplayName("consulta o par escolhido, em maiusculas")
    void consultaParEscolhido() {
        when(configuracoes.findById(anyString())).thenReturn(Optional.empty());
        fonte.expect(requestTo("https://cambio.test/v2/rate/USD/JPY"))
                .andRespond(withSuccess("{\"date\":\"2026-10-01\",\"rate\":157.61}", MediaType.APPLICATION_JSON));

        ExchangeRateResponse cotacao = servico.consultar("usd", "jpy");

        assertThat(cotacao.de()).isEqualTo("USD");
        assertThat(cotacao.para()).isEqualTo("JPY");
        assertThat(cotacao.taxa()).isEqualByComparingTo("157.61");
        assertThat(cotacao.desatualizada()).isFalse();
    }

    @Test
    @DisplayName("mesma moeda dos dois lados nao chama a fonte")
    void mesmaMoeda() {
        assertThat(servico.consultar("BRL", "BRL").taxa()).isEqualByComparingTo(BigDecimal.ONE);
        fonte.verify();
    }

    @Test
    @DisplayName("codigo que nao e ISO 4217 e recusado antes de sair para a rede")
    void recusaCodigoInvalido() {
        assertThatThrownBy(() -> servico.consultar("EUR", "../x")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> servico.consultar("XYZ", "BRL")).isInstanceOf(BusinessException.class);
        fonte.verify();
    }

    @Test
    @DisplayName("moeda que a fonte nao cobre vira aviso, nao 500")
    void moedaSemCotacao() {
        fonte.expect(requestTo("https://cambio.test/v2/rate/EUR/XAU"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> servico.consultar("EUR", "XAU"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nao tem cotacao");
    }

    @Test
    @DisplayName("fonte fora do ar devolve a ultima cotacao conhecida daquele par")
    void foraDoArUsaUltima() {
        when(configuracoes.findById("ultima_cotacao_usd_brl"))
                .thenReturn(Optional.of(new AppSetting("ultima_cotacao_usd_brl", "5.40|2026-09-30")));
        fonte.expect(requestTo("https://cambio.test/v2/rate/USD/BRL")).andRespond(withServerError());

        ExchangeRateResponse cotacao = servico.consultar("USD", "BRL");

        assertThat(cotacao.taxa()).isEqualByComparingTo("5.40");
        assertThat(cotacao.desatualizada()).isTrue();
    }

    @Test
    @DisplayName("sem nenhuma cotacao guardada, so EUR/BRL tem valor de emergencia")
    void emergenciaSoParaEurBrl() {
        when(configuracoes.findById(anyString())).thenReturn(Optional.empty());
        fonte.expect(requestTo("https://cambio.test/v2/rate/EUR/BRL")).andRespond(withServerError());
        fonte.expect(requestTo("https://cambio.test/v2/rate/USD/BRL")).andRespond(withServerError());

        assertThat(servico.consultar("EUR", "BRL").taxa()).isEqualByComparingTo("6.15");
        assertThatThrownBy(() -> servico.consultar("USD", "BRL")).isInstanceOf(UpstreamException.class);
    }

    @Test
    @DisplayName("lista de moedas vem da fonte e, fora do ar, da lista ISO local")
    void listaDeMoedas() {
        fonte.expect(requestTo("https://cambio.test/v2/currencies"))
                .andRespond(withSuccess("[{\"iso_code\":\"USD\",\"name\":\"US Dollar\"},"
                        + "{\"iso_code\":\"BRL\",\"name\":\"Brazilian Real\"}]", MediaType.APPLICATION_JSON));
        fonte.expect(requestTo("https://cambio.test/v2/currencies")).andRespond(withServerError());

        assertThat(servico.moedas()).extracting(CurrencyResponse::codigo).containsExactly("BRL", "USD");
        assertThat(servico.moedas()).extracting(CurrencyResponse::codigo).contains("EUR", "BRL", "JPY");
    }
}
