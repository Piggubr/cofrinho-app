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
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lugares, filmes, compras e marcadores de uma familia nunca chegam a outra. */
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
    @DisplayName("lugar de uma familia nao aparece, nao e editado nem apagado pela outra")
    void lugares() throws Exception {
        String lugar = """
                {"nome":"Cantina","categoria":"Outros","nota":5,"data":"2026-09-10"}
                """;
        String id = casas.criar("/api/places", casas.casaA, lugar).path("id").asString();

        mockMvc.perform(get("/api/places").with(casas.casaB)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(put("/api/places/" + id).with(casas.casaB)
                        .contentType(MediaType.APPLICATION_JSON).content(lugar))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/places/" + id).with(casas.casaB)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/places").with(casas.casaA)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("o mesmo filme cabe na lista das duas, e uma nao avalia nem apaga o da outra")
    void filmes() throws Exception {
        String filme = """
                {"tmdbId":"603","titulo":"Matrix","ano":"1999"}
                """;
        String id = casas.criar("/api/movies", casas.casaA, filme).path("id").asString();
        casas.criar("/api/movies", casas.casaB, filme);

        mockMvc.perform(get("/api/movies").with(casas.casaB))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[*].id", not(hasItem(id))));
        mockMvc.perform(put("/api/movies/" + id + "/rating").with(casas.casaB)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nota\":1}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/movies/" + id + "/watched").param("assistido", "true").with(casas.casaB))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/movies/" + id).with(casas.casaB)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("lista de compras e marcadores sao da familia")
    void comprasEMarcadores() throws Exception {
        String id = casas.criar("/api/shopping/items", casas.casaA, "{\"item\":\"Leite\"}").path("id").asString();
        casas.criar("/api/places/tags", casas.casaA, "{\"nome\":\"Romantico\"}");

        mockMvc.perform(get("/api/shopping/items").with(casas.casaB)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(patch("/api/shopping/items/" + id + "/purchased").param("comprado", "true").with(casas.casaB))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/shopping/items/" + id).with(casas.casaB)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/places/tags").with(casas.casaB)).andExpect(jsonPath("$", not(hasItem("Romantico"))));
        // A outra familia pode criar o mesmo marcador.
        casas.criar("/api/places/tags", casas.casaB, "{\"nome\":\"Romantico\"}");
    }
}
