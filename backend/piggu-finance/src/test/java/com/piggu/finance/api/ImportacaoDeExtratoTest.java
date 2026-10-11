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

    private JsonNode previa(org.springframework.test.web.servlet.request.RequestPostProcessor quem) throws Exception {
        return json.readTree(mockMvc.perform(post("/api/expenses/import/preview").with(quem)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("conteudo", CSV))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions importar(
            org.springframework.test.web.servlet.request.RequestPostProcessor quem, JsonNode previa) throws Exception {
        return mockMvc.perform(post("/api/expenses/import").with(quem).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("linhas", previa))));
    }
}
