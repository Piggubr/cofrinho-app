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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gasto em outra moeda guarda o original; divisao gera o acerto; preco acima da media e avisado. */
@AutoConfigureMockMvc
class MoedaDivisaoEPrecoTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private static final String ANA = UUID.randomUUID().toString();
    private static final String BETO = UUID.randomUUID().toString();

    private DuasFamilias casas;

    @BeforeEach
    void preparar() {
        casas = new DuasFamilias(mockMvc, json);
    }

    @Test
    @DisplayName("compra em dolar: grava o convertido e guarda o original; moeda sem valor e recusada")
    void outraMoeda() throws Exception {
        casas.criar("/api/expenses", casas.casaA, "{\"data\":\"2026-09-10\",\"itens\":[{\"item\":\"Livro\","
                + "\"valor\":110,\"moedaOriginal\":\"USD\",\"valorOriginal\":20}]}");
        mockMvc.perform(get("/api/expenses").param("mes", "2026-09").with(casas.casaA))
                .andExpect(jsonPath("$[0].valor").value(110))
                .andExpect(jsonPath("$[0].moedaOriginal").value("USD"))
                .andExpect(jsonPath("$[0].valorOriginal").value(20));
        mockMvc.perform(post("/api/expenses").with(casas.casaA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-10\",\"itens\":[{\"item\":\"X\",\"valor\":1,\"moedaOriginal\":\"USD\"}]}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("100 dividido entre duas pessoas (repetida conta uma vez): quem pagou tem 50 a receber, a outra deve 50")
    void divisao() throws Exception {
        casas.criar("/api/expenses", casas.casaA, "{\"data\":\"2026-09-10\",\"dividirCom\":"
                + "[\"" + ANA + "\",\"" + ANA + "\",\"" + BETO + "\"],\"itens\":[{\"item\":\"Jantar\",\"valor\":100}]}");
        String quemPagou = json.readTree(mockMvc.perform(get("/api/expenses").param("mes", "2026-09").with(casas.casaA))
                .andReturn().getResponse().getContentAsString()).get(0).path("usuario").asString();

        mockMvc.perform(get("/api/expenses/splits").param("mes", "2026-09").with(casas.casaA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.pessoa=='" + BETO + "')].saldo").value(-50.0))
                .andExpect(jsonPath("$[?(@.pessoa=='" + quemPagou + "')].pagou").value(100.0));
        mockMvc.perform(get("/api/expenses/splits").param("mes", "2026-09").with(casas.casaB))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("preco 20% acima da media com 2 compras e avisado; produto novo responde 204")
    void precoAcimaDaMedia() throws Exception {
        casas.criar("/api/expenses", casas.casaA, "{\"data\":\"2026-09-01\",\"itens\":[{\"item\":\"Café 500g\",\"valor\":20}]}");
        casas.criar("/api/expenses", casas.casaA, "{\"data\":\"2026-09-08\",\"itens\":[{\"item\":\"café\",\"valor\":20}]}");

        mockMvc.perform(get("/api/products/price-check").param("item", "Cafe").param("valor", "24").with(casas.casaA))
                .andExpect(jsonPath("$.acima").value(true))
                .andExpect(jsonPath("$.percentual").value(20));
        mockMvc.perform(get("/api/products/price-check").param("item", "Café").param("valor", "21").with(casas.casaA))
                .andExpect(jsonPath("$.acima").value(false));
        mockMvc.perform(get("/api/products/price-check").param("item", "Café").param("valor", "24").with(casas.casaB))
                .andExpect(status().isNoContent());
    }
}
