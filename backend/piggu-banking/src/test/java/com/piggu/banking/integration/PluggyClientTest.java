package com.piggu.banking.integration;

import com.piggu.banking.config.PluggyProperties;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Contrato HTTP com a Pluggy: autenticacao, cabecalho e leitura das respostas. */
class PluggyClientTest {

    private static final PluggyProperties CONFIGURADA =
            new PluggyProperties("https://api.pluggy.test", "id", "segredo", false);

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer pluggy = MockRestServiceServer.bindTo(builder).build();

    @Test
    @DisplayName("autentica uma vez e reaproveita a apiKey nas chamadas seguintes")
    void reaproveitaApiKey() {
        pluggy.expect(once(), requestTo("https://api.pluggy.test/auth"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"clientId\":\"id\",\"clientSecret\":\"segredo\"}"))
                .andRespond(withSuccess("{\"apiKey\":\"chave\"}", MediaType.APPLICATION_JSON));
        pluggy.expect(requestTo("https://api.pluggy.test/connect_token"))
                .andExpect(header("X-API-KEY", "chave"))
                .andExpect(content().json("{\"options\":{\"clientUserId\":\"usuario-1\"}}"))
                .andRespond(withSuccess("{\"accessToken\":\"token-1\"}", MediaType.APPLICATION_JSON));
        pluggy.expect(requestTo("https://api.pluggy.test/connect_token"))
                .andExpect(header("X-API-KEY", "chave"))
                .andRespond(withSuccess("{\"accessToken\":\"token-2\"}", MediaType.APPLICATION_JSON));

        PluggyClient cliente = new PluggyClient(builder, CONFIGURADA);

        assertThat(cliente.criarConnectToken("usuario-1")).isEqualTo("token-1");
        assertThat(cliente.criarConnectToken("usuario-1")).isEqualTo("token-2");
        pluggy.verify();
    }

    @Test
    @DisplayName("apiKey vencida e renovada antes da chamada")
    void renovaApiKeyVencida() {
        MutableClock relogio = new MutableClock();
        pluggy.expect(requestTo("https://api.pluggy.test/auth"))
                .andRespond(withSuccess("{\"apiKey\":\"velha\"}", MediaType.APPLICATION_JSON));
        pluggy.expect(requestTo("https://api.pluggy.test/connect_token"))
                .andExpect(header("X-API-KEY", "velha"))
                .andRespond(withSuccess("{\"accessToken\":\"t\"}", MediaType.APPLICATION_JSON));
        pluggy.expect(requestTo("https://api.pluggy.test/auth"))
                .andRespond(withSuccess("{\"apiKey\":\"nova\"}", MediaType.APPLICATION_JSON));
        pluggy.expect(requestTo("https://api.pluggy.test/connect_token"))
                .andExpect(header("X-API-KEY", "nova"))
                .andRespond(withSuccess("{\"accessToken\":\"t\"}", MediaType.APPLICATION_JSON));

        PluggyClient cliente = new PluggyClient(builder, CONFIGURADA, relogio);
        cliente.criarConnectToken("u");
        relogio.avancar(Duration.ofHours(2));
        cliente.criarConnectToken("u");

        pluggy.verify();
    }

    @Test
    @DisplayName("le item e contas com saldo, instituicao e dono")
    void leItemEContas() {
        pluggy.expect(requestTo("https://api.pluggy.test/auth"))
                .andRespond(withSuccess("{\"apiKey\":\"chave\"}", MediaType.APPLICATION_JSON));
        pluggy.expect(requestTo("https://api.pluggy.test/items/item-1"))
                .andRespond(withSuccess("""
                        {"id":"item-1","status":"UPDATED","clientUserId":"usuario-1",
                         "connector":{"name":"Nubank"}}
                        """, MediaType.APPLICATION_JSON));
        pluggy.expect(requestTo("https://api.pluggy.test/accounts?itemId=item-1"))
                .andRespond(withSuccess("""
                        {"total":1,"results":[{"id":"conta-1","type":"BANK","subtype":"CHECKING_ACCOUNT",
                         "name":"Conta","marketingName":"NuConta","number":"1234",
                         "balance":1520.35,"currencyCode":"BRL"}]}
                        """, MediaType.APPLICATION_JSON));

        PluggyClient cliente = new PluggyClient(builder, CONFIGURADA);

        assertThat(cliente.buscarItem("item-1"))
                .isEqualTo(new PluggyClient.Item("item-1", "UPDATED", "usuario-1", "Nubank"));
        assertThat(cliente.listarContas("item-1")).singleElement().satisfies(conta -> {
            assertThat(conta.nome()).isEqualTo("NuConta");
            assertThat(conta.tipo()).isEqualTo("CHECKING_ACCOUNT");
            assertThat(conta.saldo()).isEqualByComparingTo("1520.35");
            assertThat(conta.moeda()).isEqualTo("BRL");
        });
    }

    @Test
    @DisplayName("item inexistente na Pluggy vira 404 para o usuario")
    void itemInexistente() {
        pluggy.expect(requestTo("https://api.pluggy.test/auth"))
                .andRespond(withSuccess("{\"apiKey\":\"chave\"}", MediaType.APPLICATION_JSON));
        pluggy.expect(requestTo("https://api.pluggy.test/items/nao-existe"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        PluggyClient cliente = new PluggyClient(builder, CONFIGURADA);

        assertThatThrownBy(() -> cliente.buscarItem("nao-existe")).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("sem credenciais nao chama a Pluggy e avisa que nao esta configurado")
    void semCredenciais() {
        PluggyClient cliente = new PluggyClient(builder, new PluggyProperties(null, "", "", false));

        assertThatThrownBy(() -> cliente.criarConnectToken("u"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nao esta configurada");
        pluggy.verify();
    }

    private static final class MutableClock extends Clock {
        private Instant agora = Instant.parse("2026-10-01T12:00:00Z");

        void avancar(Duration quanto) {
            agora = agora.plus(quanto);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
