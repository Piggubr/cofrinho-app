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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

        // Fatura de outubro: compras de 5/set a 4/out. O cinema, no dia do fechamento, ja cai nela.
        mockMvc.perform(get("/api/accounts/" + cartao + "/statement").param("mes", "2026-10").with(casas.casaA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fatura.total").value(73.33))
                .andExpect(jsonPath("$.fatura.ultimaCompra").value("2026-10-04"))
                .andExpect(jsonPath("$.fatura.vencimento").value("2026-10-12"))
                .andExpect(jsonPath("$.gastos[1].parcela").value(1))
                .andExpect(jsonPath("$.gastos[1].parcelas").value(3));
        mockMvc.perform(get("/api/accounts/" + cartao + "/statement").param("mes", "2026-12").with(casas.casaA))
                .andExpect(jsonPath("$.fatura.total").value(33.34));
        mockMvc.perform(get("/api/accounts/" + cartao + "/statement").param("mes", "2026-09").with(casas.casaA))
                .andExpect(jsonPath("$.fatura.total").value(0));
        mockMvc.perform(get("/api/accounts").with(casas.casaA))
                .andExpect(jsonPath("$[0].tipo").value("CARTAO"))
                .andExpect(jsonPath("$[0].faturaAberta").exists());
    }

    @Test
    @DisplayName("cartao que fecha dia 29, 3x em 28/01: fevereiro curto nao fica sem parcela")
    void parcelasEmFevereiro() throws Exception {
        String cartao = casas.criar("/api/accounts", casas.casaA,
                "{\"nome\":\"Inter\",\"tipo\":\"CARTAO\",\"fechamento\":29,\"vencimento\":8}").path("id").asString();

        casas.criar("/api/expenses", casas.casaA, "{\"data\":\"2027-01-28\",\"contaId\":\"" + cartao
                + "\",\"parcelas\":3,\"itens\":[{\"item\":\"Geladeira\",\"valor\":300}]}");

        for (String mes : new String[] {"2027-01", "2027-02", "2027-03"}) {
            mockMvc.perform(get("/api/accounts/" + cartao + "/statement").param("mes", mes).with(casas.casaA))
                    .andExpect(jsonPath("$.fatura.total").value(100.0));
        }
    }

    @Test
    @DisplayName("fatura paga leva as anteriores junto, nao muda o total, e desfazer reabre as seguintes")
    void faturaPaga() throws Exception {
        String cartao = casas.criar("/api/accounts", casas.casaA,
                "{\"nome\":\"Nubank\",\"tipo\":\"CARTAO\",\"fechamento\":5,\"vencimento\":12}").path("id").asString();
        casas.criar("/api/expenses", casas.casaA, "{\"data\":\"2026-08-10\",\"contaId\":\"" + cartao
                + "\",\"itens\":[{\"item\":\"Fone\",\"categoria\":\"Lazer\",\"valor\":100}]}");
        String fatura = "/api/accounts/" + cartao + "/statement";

        mockMvc.perform(get(fatura).param("mes", "2026-09").with(casas.casaA))
                .andExpect(jsonPath("$.fatura.paga").value(false));
        for (int vez = 0; vez < 2; vez++) {
            mockMvc.perform(put(fatura + "/paid").param("mes", "2026-09").with(casas.casaA))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(get(fatura).param("mes", "2026-09").with(casas.casaA))
                .andExpect(jsonPath("$.fatura.paga").value(true))
                .andExpect(jsonPath("$.fatura.total").value(100.0));
        mockMvc.perform(get(fatura).param("mes", "2026-08").with(casas.casaA))
                .andExpect(jsonPath("$.fatura.paga").value(true));
        mockMvc.perform(get(fatura).param("mes", "2026-10").with(casas.casaA))
                .andExpect(jsonPath("$.fatura.paga").value(false));
        mockMvc.perform(get("/api/expenses").param("mes", "2026-08").with(casas.casaA))
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(delete(fatura + "/paid").param("mes", "2026-08").with(casas.casaA))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(fatura).param("mes", "2026-08").with(casas.casaA))
                .andExpect(jsonPath("$.fatura.paga").value(false));
        mockMvc.perform(get(fatura).param("mes", "2026-07").with(casas.casaA))
                .andExpect(jsonPath("$.fatura.paga").value(true));

        mockMvc.perform(put(fatura + "/paid").param("mes", "2099-01").with(casas.casaA))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(put(fatura + "/paid").param("mes", "2026-09").with(casas.casaB))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(fatura + "/paid").with(casas.casaA)).andExpect(status().isBadRequest());
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
