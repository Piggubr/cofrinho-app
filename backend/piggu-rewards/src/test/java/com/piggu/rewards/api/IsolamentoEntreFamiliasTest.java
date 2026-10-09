package com.piggu.rewards.api;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Saldo de Fofocoins, premios e resgates sao de cada familia. */
@AutoConfigureMockMvc
class IsolamentoEntreFamiliasTest extends PostgresIntegrationTest {

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
    @DisplayName("moedas de uma familia nao pagam premio da outra, e premio alheio nao e visto nem mexido")
    void fofocoins() throws Exception {
        casas.criar("/api/coins/adjustments", casas.casaA, "{\"valor\":100,\"motivo\":\"mesada\"}");
        String premio = casas.criar("/api/prizes", casas.casaA, "{\"nome\":\"Cinema\",\"preco\":50}").path("id").asString();

        mockMvc.perform(get("/api/coins").with(casas.casaB)).andExpect(jsonPath("$.saldo").value(0));
        mockMvc.perform(get("/api/prizes").with(casas.casaB)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(post("/api/prizes/" + premio + "/redemptions").with(casas.casaB)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/prizes/" + premio).with(casas.casaB)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Gratis\",\"preco\":1}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/prizes/" + premio).with(casas.casaB)).andExpect(status().isNotFound());

        // A outra familia credita o proprio saldo e nao enxerga o da primeira.
        casas.criar("/api/coins/adjustments", casas.casaB, "{\"valor\":7,\"motivo\":\"teste\"}");
        mockMvc.perform(get("/api/coins").with(casas.casaB)).andExpect(jsonPath("$.saldo").value(7));
        mockMvc.perform(get("/api/coins").with(casas.casaA)).andExpect(jsonPath("$.saldo").value(100));

        mockMvc.perform(post("/api/prizes/" + premio + "/redemptions").with(casas.casaA)).andExpect(status().isCreated());
        mockMvc.perform(get("/api/prizes/redemptions").with(casas.casaB)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/coins").with(casas.casaA)).andExpect(jsonPath("$.saldo").value(50));
    }
}
