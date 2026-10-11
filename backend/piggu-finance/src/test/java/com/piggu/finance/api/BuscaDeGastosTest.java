package com.piggu.finance.api;

import com.piggu.testing.DuasFamilias;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** Busca global: item ou estabelecimento, sem diferenciar maiusculas, so da propria familia. */
@AutoConfigureMockMvc
class BuscaDeGastosTest extends PostgresIntegrationTest {

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
    @DisplayName("acha pelo item e pelo estabelecimento; curinga % nao vira 'tudo'; outra familia nao ve")
    void buscar() throws Exception {
        casas.criar("/api/expenses", casas.casaA, "{\"data\":\"2026-09-10\",\"estabelecimento\":\"Padaria Central\","
                + "\"itens\":[{\"item\":\"Pão francês\",\"valor\":8}]}");
        casas.criar("/api/expenses", casas.casaA, "{\"data\":\"2026-09-11\",\"itens\":[{\"item\":\"Gasolina\",\"valor\":200}]}");

        mockMvc.perform(get("/api/expenses/search").param("q", "PADARIA").with(casas.casaA))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].item").value("Pão francês"));
        mockMvc.perform(get("/api/expenses/search").param("q", "gasol").with(casas.casaA))
                .andExpect(jsonPath("$[0].valor").value(200));
        mockMvc.perform(get("/api/expenses/search").param("q", "%%").with(casas.casaA))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/expenses/search").param("q", "gasol").with(casas.casaB))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
