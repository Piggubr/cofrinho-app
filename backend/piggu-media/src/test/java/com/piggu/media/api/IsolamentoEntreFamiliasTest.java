package com.piggu.media.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.piggu.testing.DuasFamilias;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A foto de uma familia nunca e entregue, legendada ou apagada por outra. */
@AutoConfigureMockMvc
class IsolamentoEntreFamiliasTest extends PostgresIntegrationTest {

    /** PNG de 1x1 pixel. */
    private static final String PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

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
    @DisplayName("foto por id e foto do mural nao cruzam familias")
    void fotos() throws Exception {
        String asset = casas.criar("/api/assets", casas.casaA,
                "{\"imageBase64\":\"" + PNG + "\",\"mimeType\":\"image/png\",\"contexto\":\"LUGAR\"}").path("id").asText();
        String foto = casas.criar("/api/feed", casas.casaA,
                "{\"mesKey\":\"2026-09\",\"imageBase64\":\"" + PNG + "\",\"mimeType\":\"image/png\"}").path("id").asText();

        mockMvc.perform(get("/api/assets/" + asset + "/content").with(casas.casaB)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/assets/" + asset).with(casas.casaB)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/feed").with(casas.casaB)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(patch("/api/feed/" + foto + "/caption").with(casas.casaB)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"legenda\":\"invasao\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/feed/" + foto).with(casas.casaB)).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/assets/" + asset + "/content").with(casas.casaA)).andExpect(status().isOk());
        mockMvc.perform(get("/api/feed").with(casas.casaA)).andExpect(jsonPath("$.length()").value(1));
    }
}
