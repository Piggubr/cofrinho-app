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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Categoria automatica: regra da familia, depois a memoria do produto; a escolha manual vence. */
@AutoConfigureMockMvc
class RegrasDeCategoriaTest extends PostgresIntegrationTest {

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
    @DisplayName("regra pelo item ou pelo estabelecimento; o termo mais longo vence; escolha manual e respeitada")
    void regras() throws Exception {
        regra(casas.casaA, "Uber", "Transporte").andExpect(jsonPath("$.termo").value("uber"));
        regra(casas.casaA, "pão", "Alimentação");
        regra(casas.casaA, "Pão de Queijo", "Lazer");

        lancar(casas.casaA, "Manual", null, "Uber Centro", null).andExpect(jsonPath("$[0].categoria").value("Transporte"));
        lancar(casas.casaA, "Foto", "Padaria", "Pão de queijo", "Outros")
                .andExpect(jsonPath("$[0].categoria").value("Lazer"));
        lancar(casas.casaA, "Foto", "Uber", "Corrida", "Outros").andExpect(jsonPath("$[0].categoria").value("Transporte"));
        lancar(casas.casaA, "Manual", null, "Uber", "Lazer").andExpect(jsonPath("$[0].categoria").value("Lazer"));
        // A regra e da familia: a outra casa nao a ve nem usa.
        lancar(casas.casaB, "Manual", null, "Uber", null).andExpect(jsonPath("$[0].categoria").value("Outros"));
        mockMvc.perform(get("/api/categories/rules").with(casas.casaB)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("sem regra e sem categoria, vale a da ultima compra do produto; regra troca e apaga")
    void memoriaETroca() throws Exception {
        lancar(casas.casaA, "Manual", null, "Ração do gato", "Lazer");
        lancar(casas.casaA, "Manual", null, "ração do gato", null).andExpect(jsonPath("$[0].categoria").value("Lazer"));

        regra(casas.casaA, "racao", "Alimentação");
        String id = regra(casas.casaA, "ração", "Transporte").andExpect(jsonPath("$.categoria").value("Transporte"))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(get("/api/categories/rules").with(casas.casaA)).andExpect(jsonPath("$.length()").value(1));

        String regraId = json.readTree(id).path("id").asString();
        mockMvc.perform(delete("/api/categories/rules/" + regraId).with(casas.casaB)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/categories/rules/" + regraId).with(casas.casaA)).andExpect(status().isNoContent());
        regra(casas.casaA, " ! ", "Lazer").andExpect(status().isUnprocessableContent());
    }

    private ResultActions regra(RequestPostProcessor quem, String termo, String categoria) throws Exception {
        return mockMvc.perform(put("/api/categories/rules").with(quem).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("termo", termo, "categoria", categoria))));
    }

    private ResultActions lancar(RequestPostProcessor quem, String origem, String estabelecimento, String item,
                                 String categoria) throws Exception {
        var itemJson = new java.util.HashMap<String, Object>();
        itemJson.put("item", item);
        itemJson.put("valor", 10);
        itemJson.put("categoria", categoria);
        var corpo = new java.util.HashMap<String, Object>();
        corpo.put("data", "2026-09-10");
        corpo.put("origem", origem);
        corpo.put("estabelecimento", estabelecimento);
        corpo.put("itens", java.util.List.of(itemJson));
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/expenses")
                        .with(quem).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo)))
                .andExpect(status().is2xxSuccessful());
    }
}
