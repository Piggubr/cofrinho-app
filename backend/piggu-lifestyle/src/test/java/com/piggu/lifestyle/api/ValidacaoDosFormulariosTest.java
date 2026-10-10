package com.piggu.lifestyle.api;

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
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Dado fora do formato e barrado na entrada (400 com o campo), antes de chegar ao banco. */
@AutoConfigureMockMvc
class ValidacaoDosFormulariosTest extends PostgresIntegrationTest {

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
    @DisplayName("filme sem titulo ou com nota fora de 0 a 10, lista de compras inventada e marcacoes demais")
    void foraDoFormato() throws Exception {
        enviar("/api/movies", "{\"titulo\":\"\",\"nota\":5}").andExpect(status().isBadRequest());
        enviar("/api/movies", "{\"titulo\":\"Duna\",\"nota\":99999}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("nota"));
        enviar("/api/shopping/items", "{\"item\":\"Leite\",\"lista\":\"Qualquer\"}").andExpect(status().isBadRequest());
        enviar("/api/places", "{\"nome\":\"Bar\",\"nota\":4,\"data\":\"2026-09-10\",\"marcacoes\":["
                + "\"a\",\"a\",\"a\",\"a\",\"a\",\"a\",\"a\",\"a\",\"a\",\"a\",\"a\",\"a\",\"a\",\"a\",\"a\",\"a\",\"a\",\"a\",\"a\",\"a\",\"a\"]}")
                .andExpect(status().isBadRequest());
        enviar("/api/places", "{\"nome\":\"Bar\",\"nota\":4,\"data\":\"2026-09-10\",\"mimeType\":\"text/html\"}")
                .andExpect(status().isBadRequest());
    }

    private ResultActions enviar(String rota, String corpo) throws Exception {
        return mockMvc.perform(post(rota).with(casas.casaA).contentType(MediaType.APPLICATION_JSON).content(corpo));
    }
}
