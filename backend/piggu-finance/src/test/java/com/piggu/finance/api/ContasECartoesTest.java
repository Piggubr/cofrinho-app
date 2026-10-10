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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cartao: parcelas viram uma linha por mes e caem cada uma na sua fatura. */
@AutoConfigureMockMvc
class ContasECartoesTest extends PostgresIntegrationTest {

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
    @DisplayName("100 em 3x no cartao: 33,33 + 33,33 + 33,34, uma por fatura")
    void parcelasNaFatura() throws Exception {
        String cartao = casas.criar("/api/accounts", casas.casaA,
                "{\"nome\":\"Nubank\",\"tipo\":\"CARTAO\",\"fechamento\":5,\"vencimento\":12}").path("id").asString();

        casas.criar("/api/expenses", casas.casaA, "{\"data\":\"2026-09-10\",\"contaId\":\"" + cartao
                + "\",\"parcelas\":3,\"itens\":[{\"item\":\"Fone\",\"categoria\":\"Lazer\",\"valor\":100}]}");
        casas.criar("/api/expenses", casas.casaA, "{\"data\":\"2026-09-05\",\"contaId\":\"" + cartao
                + "\",\"itens\":[{\"item\":\"Cinema\",\"categoria\":\"Lazer\",\"valor\":40}]}");

        // Compra de 10/set: fatura de outubro (6/set a 5/out). Cinema de 5/set: fatura de setembro.
        mockMvc.perform(get("/api/accounts/" + cartao + "/statement").param("mes", "2026-10").with(casas.casaA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fatura.total").value(33.33))
                .andExpect(jsonPath("$.fatura.vencimento").value("2026-10-12"))
                .andExpect(jsonPath("$.gastos[0].parcela").value(1))
                .andExpect(jsonPath("$.gastos[0].parcelas").value(3));
        mockMvc.perform(get("/api/accounts/" + cartao + "/statement").param("mes", "2026-12").with(casas.casaA))
                .andExpect(jsonPath("$.fatura.total").value(33.34));
        mockMvc.perform(get("/api/accounts/" + cartao + "/statement").param("mes", "2026-09").with(casas.casaA))
                .andExpect(jsonPath("$.fatura.total").value(40));
        mockMvc.perform(get("/api/accounts").with(casas.casaA))
                .andExpect(jsonPath("$[0].tipo").value("CARTAO"))
                .andExpect(jsonPath("$[0].faturaAberta").exists());
    }

    @Test
    @DisplayName("cartao de outra familia nao serve para lancar nem para ver fatura; conta apagada solta os gastos")
    void isolamento() throws Exception {
        String conta = casas.criar("/api/accounts", casas.casaA, "{\"nome\":\"Conta corrente\",\"tipo\":\"CONTA\"}")
                .path("id").asString();

        mockMvc.perform(post("/api/expenses").with(casas.casaB).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-10\",\"contaId\":\"" + conta
                                + "\",\"itens\":[{\"item\":\"X\",\"valor\":1}]}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/accounts/" + conta + "/statement").with(casas.casaB)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/accounts").with(casas.casaB)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(post("/api/accounts").with(casas.casaA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Visa\",\"tipo\":\"CARTAO\"}"))
                .andExpect(status().isUnprocessableContent());

        casas.criar("/api/expenses", casas.casaA, "{\"data\":\"2026-09-10\",\"contaId\":\"" + conta
                + "\",\"itens\":[{\"item\":\"Padaria\",\"valor\":12}]}");
        mockMvc.perform(delete("/api/accounts/" + conta).with(casas.casaA)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/expenses").param("mes", "2026-09").with(casas.casaA))
                .andExpect(jsonPath("$[0].item").value("Padaria"))
                .andExpect(jsonPath("$[0].contaId").doesNotExist());
    }
}
