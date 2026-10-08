package com.piggu.finance.api;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uma familia nunca le nem mexe no que e de outra.
 *
 * <p>A titular de cada familia tem Premium e o papel mais alto possivel dentro de casa:
 * se mesmo assim nao alcanca o dado alheio, ninguem alcanca.</p>
 */
@AutoConfigureMockMvc
class IsolamentoEntreFamiliasTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private RequestPostProcessor casaA;
    private RequestPostProcessor casaB;
    private DuasFamilias casas;

    @BeforeEach
    void preparar() {
        casas = new DuasFamilias(mockMvc, json);
        casaA = casas.casaA;
        casaB = casas.casaB;
    }

    @Test
    @DisplayName("gasto de uma familia nao aparece, nao e editado nem apagado pela outra")
    void gastos() throws Exception {
        String id = criar("/api/expenses", casaA, """
                {"data":"2026-09-10","itens":[{"item":"Pao","categoria":"Mercado","valor":10}]}
                """).get(0).path("id").asText();

        mockMvc.perform(get("/api/expenses").with(casaB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(put("/api/expenses/" + id).with(casaB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"item\":\"Hackeado\",\"valor\":1}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/expenses/" + id).with(casaB)).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/expenses").with(casaA))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].item").value("Pao"));
    }

    @Test
    @DisplayName("meta do mes e por familia: as duas definem o mesmo mes sem conflito")
    void metaPorFamilia() throws Exception {
        definirMeta(casaA, 500);
        definirMeta(casaB, 900);

        mockMvc.perform(get("/api/monthly-goals").with(casaA)).andExpect(jsonPath("$['2026-09']").value(500));
        mockMvc.perform(get("/api/monthly-goals").with(casaB)).andExpect(jsonPath("$['2026-09']").value(900));
    }

    @Test
    @DisplayName("cofrinho, notas, produtos e categorias nao cruzam familias")
    void demaisRecursos() throws Exception {
        String deposito = criar("/api/piggy-bank/deposits", casaA, "{\"data\":\"2026-09-01\",\"valor\":100}")
                .path("id").asText();
        String nota = criar("/api/notes", casaA, "{\"titulo\":\"Segredo\",\"texto\":\"so da casa A\"}")
                .path("id").asText();
        criar("/api/expenses", casaA, """
                {"data":"2026-09-10","itens":[{"item":"Cafe especial","categoria":"Mercado","valor":30}]}
                """);
        mockMvc.perform(post("/api/categories").with(casaA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Viagem\"}"))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get("/api/piggy-bank").with(casaB))
                .andExpect(jsonPath("$.depositos.length()").value(0))
                .andExpect(jsonPath("$.totalDepositos").value(0));
        mockMvc.perform(delete("/api/piggy-bank/deposits/" + deposito).with(casaB)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/notes").with(casaB)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(delete("/api/notes/" + nota).with(casaB)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/products").with(casaB)).andExpect(jsonPath("$.length()").value(0));

        JsonNode categorias = json.readTree(mockMvc.perform(get("/api/categories").with(casaB))
                .andReturn().getResponse().getContentAsString());
        assertThat(categorias.toString()).doesNotContain("Viagem");

        // A outra familia cria a mesma categoria: o nome so e unico dentro de casa.
        mockMvc.perform(post("/api/categories").with(casaB)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Viagem\"}"))
                .andExpect(status().is2xxSuccessful());
    }

    private JsonNode criar(String rota, RequestPostProcessor quem, String corpo) throws Exception {
        return casas.criar(rota, quem, corpo);
    }

    private void definirMeta(RequestPostProcessor quem, int limite) throws Exception {
        mockMvc.perform(put("/api/monthly-goals").with(quem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mes\":\"2026-09\",\"limite\":" + limite + "}"))
                .andExpect(status().is2xxSuccessful());
    }
}
