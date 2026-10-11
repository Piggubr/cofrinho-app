package com.piggu.finance.assistente;

import com.piggu.common.error.UpstreamException;
import com.piggu.finance.config.IntegracoesProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Rodadas de function calling com o Gemini simulado. */
class ConversaComGeminiTest {

    private static final String URL = "https://gemini.test/v1beta/models/modelo:generateContent";
    private static final String PEDE_FERRAMENTA = """
            {"candidates":[{"content":{"role":"model","parts":[
              {"functionCall":{"id":"c1","name":"gastos_do_mes","args":{"mes":"2026-09"}},
               "thoughtSignature":"assinatura"}]}}]}
            """;

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer gemini = MockRestServiceServer.bindTo(builder).build();
    private final ConversaComGemini conversa = new ConversaComGemini(builder,
            new IntegracoesProperties(new IntegracoesProperties.Gemini("https://gemini.test", "chave", "modelo", null), null),
            new ObjectMapper());

    @Test
    @DisplayName("o modelo pede a ferramenta, recebe o resultado com a assinatura de volta e responde")
    void rodadaDeFerramenta() {
        gemini.expect(requestTo(URL))
                .andExpect(header("x-goog-api-key", "chave"))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Oi"))
                .andExpect(jsonPath("$.contents[1].role").value("model"))
                .andExpect(jsonPath("$.contents[2].parts[0].text").value("Quanto gastei?"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("instrucoes"))
                .andExpect(jsonPath("$.tools[0].functionDeclarations[0].name").value("gastos_do_mes"))
                .andRespond(withSuccess(PEDE_FERRAMENTA, MediaType.APPLICATION_JSON));
        gemini.expect(requestTo(URL))
                .andExpect(jsonPath("$.contents[3].parts[0].thoughtSignature").value("assinatura"))
                .andExpect(jsonPath("$.contents[4].parts[0].functionResponse.id").value("c1"))
                .andExpect(jsonPath("$.contents[4].parts[0].functionResponse.response.resultado.total").value(42))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"role":"model","parts":[
                          {"text":"pensando","thought":true},{"text":"Você gastou R$ 42 em setembro."}]}}]}
                        """, MediaType.APPLICATION_JSON));

        List<String> chamadas = new ArrayList<>();
        String resposta = conversa.responder("instrucoes",
                List.of(new ConversaComGemini.Fala(true, "Oi"), new ConversaComGemini.Fala(false, "Olá!")),
                "Quanto gastei?", List.of(Map.of("name", "gastos_do_mes")),
                (nome, argumentos) -> {
                    chamadas.add(nome + " " + argumentos.path("mes").asString());
                    return Map.of("total", 42);
                });

        assertThat(resposta).isEqualTo("Você gastou R$ 42 em setembro.");
        assertThat(chamadas).containsExactly("gastos_do_mes 2026-09");
        gemini.verify();
    }

    @Test
    @DisplayName("modelo que so pede ferramenta para no limite de rodadas, sem resposta inventada")
    void limiteDeRodadas() {
        gemini.expect(times(ConversaComGemini.MAXIMO_DE_RODADAS), requestTo(URL))
                .andRespond(withSuccess(PEDE_FERRAMENTA, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> conversa.responder("i", List.of(), "?", List.of(), (n, a) -> Map.of()))
                .isInstanceOf(UpstreamException.class)
                .hasMessageContaining("Nao consegui chegar");
    }
}
