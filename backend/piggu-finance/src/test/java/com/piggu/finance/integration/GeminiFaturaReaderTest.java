package com.piggu.finance.integration;

import com.piggu.common.error.UpstreamException;
import com.piggu.finance.config.IntegracoesProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Reserva da leitura de fatura: o Gemini recebe so o texto. */
class GeminiFaturaReaderTest {

    private static final String URL = "https://gemini.test/v1beta/models/modelo:generateContent";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer gemini = MockRestServiceServer.bindTo(builder).build();

    private GeminiFaturaReader leitor() {
        return new GeminiFaturaReader(builder,
                new IntegracoesProperties(new IntegracoesProperties.Gemini("https://gemini.test", "chave", "modelo", null), null),
                new ObjectMapper());
    }

    private static final String RESPOSTA = """
            {"candidates":[{"content":{"parts":[{"text":\
            "{\\"banco\\":\\"nubank\\",\\"vencimento\\":\\"2026-11-10\\",\\"total\\":\\"150.50\\",\\"linhas\\":[\
              {\\"data\\":\\"2026-10-05\\",\\"descricao\\":\\"Mercado\\",\\"valor\\":100.00,\\"tipo\\":\\"compra\\"},\
              {\\"data\\":\\"2026-10-06\\",\\"descricao\\":\\"Tênis\\",\\"valor\\":-50.50,\\"tipo\\":\\"COMPRA\\",\\"parcelaNumero\\":2,\\"parcelaTotal\\":3},\
              {\\"data\\":\\"data ruim\\",\\"descricao\\":\\"X\\",\\"valor\\":1,\\"tipo\\":\\"COMPRA\\"},\
              {\\"data\\":\\"2026-10-07\\",\\"descricao\\":\\"Y\\",\\"valor\\":1,\\"tipo\\":\\"OUTRO\\"}]}"
            }]}}]}
            """;

    @Test
    @DisplayName("le a resposta, descarta linha invalida e gera os ids")
    void leRespostaDoGemini() {
        gemini.expect(requestTo(URL)).andExpect(header("x-goog-api-key", "chave"))
                .andRespond(withSuccess(RESPOSTA, MediaType.APPLICATION_JSON));

        LeitorDeFatura.Leitura leitura = leitor().ler("texto da fatura");

        assertThat(leitura.banco()).isEqualTo("nubank");
        assertThat(leitura.vencimento()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(leitura.total()).isEqualByComparingTo("150.50");
        assertThat(leitura.linhas()).hasSize(2);
        assertThat(leitura.linhas().get(1).valor()).isEqualByComparingTo("50.50");
        assertThat(leitura.linhas().get(1).parcelaNumero()).isEqualTo(2);
        assertThat(leitura.linhas()).allSatisfy(l -> assertThat(l.idExterno()).startsWith("fatura:"));
        assertThat(leitura.fecha()).isTrue();
    }

    @Test
    @DisplayName("manda so o texto, nunca arquivo")
    void mandaSoTexto() {
        gemini.expect(requestTo(URL))
                .andExpect(content().string(containsString("texto da fatura")))
                .andExpect(content().string(not(containsString("inline_data"))))
                .andRespond(withSuccess(RESPOSTA, MediaType.APPLICATION_JSON));

        leitor().ler("texto da fatura");
        gemini.verify();
    }

    @Test
    @DisplayName("erro do Gemini vira mensagem da fatura")
    void erroDoGeminiViraMensagem() {
        gemini.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> leitor().ler("texto"))
                .isInstanceOf(UpstreamException.class)
                .hasMessageContaining("fatura")
                .hasMessageNotContaining("recibo");
    }

    @Test
    @DisplayName("400 na fatura nao fala em imagem; no recibo continua igual")
    void erro400SemImagemNaFatura() {
        assertThat(GeminiReceiptReader.traduzirErro(400, "a fatura").getMessage())
                .doesNotContain("imagem").contains("a fatura");
        assertThat(GeminiReceiptReader.traduzirErro(400, "o recibo").getMessage())
                .isEqualTo("O Gemini recusou o formato da imagem ou do pedido.");
    }
}
