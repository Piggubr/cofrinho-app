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
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Com sinal fraco a resposta se perde e o app manda o mesmo gasto de novo, com o mesmo idDoEnvio. */
@AutoConfigureMockMvc
class EnvioRepetidoTest extends PostgresIntegrationTest {

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
    @DisplayName("o mesmo idDoEnvio duas vezes grava uma vez e responde igual; outra familia com o mesmo id grava a dela")
    void mesmoEnvio() throws Exception {
        String corpo = "{\"data\":\"2026-09-10\",\"idDoEnvio\":\"" + UUID.randomUUID() + "\","
                + "\"itens\":[{\"item\":\"Pao\",\"valor\":10},{\"item\":\"Leite\",\"valor\":6}]}";

        String primeira = mockMvc.perform(post("/api/expenses").with(casas.casaA)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/expenses").with(casas.casaA).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andExpect(content().json(primeira, true));
        mockMvc.perform(post("/api/expenses").with(casas.casaB).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/expenses").param("mes", "2026-09").with(casas.casaA))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/expenses").param("mes", "2026-09").with(casas.casaB))
                .andExpect(jsonPath("$.length()").value(2));
    }
}
