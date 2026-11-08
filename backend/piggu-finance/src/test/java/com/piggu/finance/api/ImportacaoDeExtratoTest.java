package com.piggu.finance.api;

import com.piggu.testing.DuasFamilias;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Importar extrato: previa, gravacao, reimportacao pulada e exportacao em CSV. */
@AutoConfigureMockMvc
class ImportacaoDeExtratoTest extends PostgresIntegrationTest {

    private static final String CSV = "data;descricao;valor\n10/09/2026;Uber Centro;-23,50\n11/09/2026;Mercado;-100,00\n";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private DuasFamilias casas;

    @BeforeEach
    void preparar() {
        casas = new DuasFamilias(mockMvc, json);
    }

    @Test
    @DisplayName("previa usa a regra de categoria; importar de novo pula o que ja entrou; outra familia importa do zero")
    void importar() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/categories/rules")
                .with(casas.casaA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"termo\":\"uber\",\"categoria\":\"Transporte\"}")).andExpect(status().isOk());

        JsonNode previa = previa(casas.casaA);
        assertThat(previa.get(0).path("categoria").asString()).isEqualTo("Transporte");
        assertThat(previa.get(0).path("jaImportada").asBoolean()).isFalse();

        importar(casas.casaA, previa).andExpect(jsonPath("$.importados").value(2));
        assertThat(previa(casas.casaA).get(0).path("jaImportada").asBoolean()).isTrue();
        importar(casas.casaA, previa).andExpect(jsonPath("$.importados").value(0)).andExpect(jsonPath("$.pulados").value(2));
        importar(casas.casaB, previa).andExpect(jsonPath("$.importados").value(2));

        mockMvc.perform(get("/api/expenses/export").param("mes", "2026-09").with(casas.casaA))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"Uber Centro\";\"Transporte\";23,50")));
    }

    @Test
    @DisplayName("a mesma linha em dois cartoes entra nos dois; no mesmo cartao, so uma vez")
    void mesmaLinhaEmDoisCartoes() throws Exception {
        String nubank = casas.criar("/api/accounts", casas.casaA,
                "{\"nome\":\"Nubank\",\"tipo\":\"CARTAO\",\"fechamento\":5,\"vencimento\":12}").path("id").asString();
        String inter = casas.criar("/api/accounts", casas.casaA,
                "{\"nome\":\"Inter\",\"tipo\":\"CARTAO\",\"fechamento\":5,\"vencimento\":12}").path("id").asString();
        JsonNode previa = previa(casas.casaA);

        importar(casas.casaA, previa, nubank).andExpect(jsonPath("$.importados").value(2));
        importar(casas.casaA, previa, nubank).andExpect(jsonPath("$.importados").value(0));
        importar(casas.casaA, previa, inter).andExpect(jsonPath("$.importados").value(2));
        importar(casas.casaA, previa, inter).andExpect(jsonPath("$.importados").value(0));
        // A previa com a conta so marca o que entrou nela.
        assertThat(previa(casas.casaA, nubank).get(0).path("jaImportada").asBoolean()).isTrue();
        String c6 = casas.criar("/api/accounts", casas.casaA,
                "{\"nome\":\"C6\",\"tipo\":\"CARTAO\",\"fechamento\":5,\"vencimento\":12}").path("id").asString();
        assertThat(previa(casas.casaA, c6).get(0).path("jaImportada").asBoolean()).isFalse();

        // Apagar os cartoes deixa as linhas sem conta: as dos dois nao podem colidir.
        importar(casas.casaA, previa).andExpect(jsonPath("$.importados").value(2));
        for (String cartao : new String[] {nubank, inter}) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .delete("/api/accounts/" + cartao).with(casas.casaA)).andExpect(status().isNoContent());
        }
        importar(casas.casaA, previa).andExpect(jsonPath("$.importados").value(0));
    }

    @Test
    @DisplayName("gastos do banco conectado: previa por linhas, com regra, e o que ja entrou fica marcado")
    void previaDeLinhasDoBanco() throws Exception {
        String linhas = """
                {"linhas":[{"data":"2026-09-12","descricao":"Uber Centro","valor":18.4,"idExterno":"pluggy:t1"},
                           {"data":"2026-09-13","descricao":"Padaria","valor":9.9,"idExterno":"pluggy:t2"}]}
                """;
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/categories/rules")
                .with(casas.casaA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"termo\":\"uber\",\"categoria\":\"Transporte\"}")).andExpect(status().isOk());

        JsonNode previa = previaDeLinhas(linhas);
        assertThat(previa.get(0).path("categoria").asString()).isEqualTo("Transporte");
        importar(casas.casaA, previa).andExpect(jsonPath("$.importados").value(2));
        assertThat(previaDeLinhas(linhas).findValuesAsString("jaImportada")).containsOnly("true");

        mockMvc.perform(post("/api/expenses/import/preview-lines").with(casas.casaA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"linhas\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("parcela 3/5 importada grava ela e as seguintes (3/5, 4/5, 5/5) no mesmo lote; conta como uma linha")
    void importarParcelaGravaAsSeguintes() throws Exception {
        String cartao = casas.criar("/api/accounts", casas.casaA,
                "{\"nome\":\"Inter\",\"tipo\":\"CARTAO\",\"fechamento\":1,\"vencimento\":10}").path("id").asString();
        String corpo = "{\"contaId\":\"" + cartao + "\",\"linhas\":[{\"data\":\"2026-09-05\",\"descricao\":\"Loja Parc\","
                + "\"valor\":100,\"idExterno\":\"fatura:abc\",\"parcelaNumero\":3,\"parcelaTotal\":5}]}";
        mockMvc.perform(post("/api/expenses/import").with(casas.casaA).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(jsonPath("$.importados").value(1));

        JsonNode gastos = json.readTree(mockMvc.perform(get("/api/expenses").with(casas.casaA))
                .andReturn().getResponse().getContentAsString());
        assertThat(gastos.size()).isEqualTo(3);
        assertThat(gastos.findValuesAsString("parcela")).containsExactlyInAnyOrder("3", "4", "5");
        assertThat(gastos.findValuesAsString("parcelas")).containsOnly("5");
        assertThat(gastos.findValuesAsString("data")).containsExactlyInAnyOrder("2026-11-05", "2026-12-05", "2027-01-05");
        assertThat(gastos.findValuesAsString("reciboId")).hasSize(3).containsOnly(gastos.get(0).path("reciboId").asString());

        // De novo: a linha ja entrou pelo idExterno, nada duplica.
        mockMvc.perform(post("/api/expenses/import").with(casas.casaA).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(jsonPath("$.importados").value(0));
        // So numero ou parcela maior que o total: pedido invalido.
        mockMvc.perform(post("/api/expenses/import").with(casas.casaA).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo.replace(",\"parcelaTotal\":5", "")))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/expenses/import").with(casas.casaA).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo.replace("\"parcelaTotal\":5", "\"parcelaTotal\":49")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("apagou so a 3/5 e importou a linha de novo: volta a 3/5, a 4/5 e a 5/5 nao duplicam")
    void reimportarParcelaNaoDuplicaAsSeguintes() throws Exception {
        String corpo = "{\"linhas\":[{\"data\":\"2026-09-05\",\"descricao\":\"Loja Parc\","
                + "\"valor\":100,\"idExterno\":\"fatura:def\",\"parcelaNumero\":3,\"parcelaTotal\":5}]}";
        mockMvc.perform(post("/api/expenses/import").with(casas.casaA).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(jsonPath("$.importados").value(1));
        JsonNode gastos = gastos();
        String terceira = null;
        for (JsonNode g : gastos) {
            if (g.path("parcela").asInt() == 3) {
                terceira = g.path("id").asString();
            }
        }
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/expenses/" + terceira)
                .with(casas.casaA)).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/expenses/import").with(casas.casaA).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(jsonPath("$.importados").value(1));
        assertThat(gastos().findValuesAsString("parcela")).containsExactlyInAnyOrder("3", "4", "5");
    }

    private JsonNode gastos() throws Exception {
        return json.readTree(mockMvc.perform(get("/api/expenses").with(casas.casaA))
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode previaDeLinhas(String corpo) throws Exception {
        return json.readTree(mockMvc.perform(post("/api/expenses/import/preview-lines").with(casas.casaA)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode previa(org.springframework.test.web.servlet.request.RequestPostProcessor quem) throws Exception {
        return previa(quem, Map.of("conteudo", CSV));
    }

    private JsonNode previa(org.springframework.test.web.servlet.request.RequestPostProcessor quem, String conta)
            throws Exception {
        return previa(quem, Map.of("conteudo", CSV, "contaId", conta));
    }

    private JsonNode previa(org.springframework.test.web.servlet.request.RequestPostProcessor quem, Map<String, String> corpo)
            throws Exception {
        return json.readTree(mockMvc.perform(post("/api/expenses/import/preview").with(quem)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions importar(
            org.springframework.test.web.servlet.request.RequestPostProcessor quem, JsonNode previa) throws Exception {
        return mockMvc.perform(post("/api/expenses/import").with(quem).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("linhas", previa))));
    }

    private org.springframework.test.web.servlet.ResultActions importar(
            org.springframework.test.web.servlet.request.RequestPostProcessor quem, JsonNode previa, String conta)
            throws Exception {
        return mockMvc.perform(post("/api/expenses/import").with(quem).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("linhas", previa, "contaId", conta))));
    }
}
