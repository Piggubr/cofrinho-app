package com.piggu.finance.api;

import com.piggu.common.web.Meses;
import com.piggu.testing.DuasFamilias;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fechamento pelo vencimento, gasto e receita recorrentes, parcelas apagadas juntas e dinheiro a receber. */
@AutoConfigureMockMvc
class RecorrenciaEAReceberTest extends PostgresIntegrationTest {

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
    @DisplayName("cartao so com o vencimento fecha 7 dias antes")
    void fechamentoPeloVencimento() throws Exception {
        JsonNode cartao = casas.criar("/api/accounts", casas.casaA,
                "{\"nome\":\"Inter\",\"tipo\":\"CARTAO\",\"vencimento\":15}");
        org.assertj.core.api.Assertions.assertThat(cartao.path("fechamento").asInt()).isEqualTo(8);
    }

    @Test
    @DisplayName("gasto recorrente no cartao entra agora e vira conta fixa automatica que paga com o mesmo cartao")
    void gastoRecorrente() throws Exception {
        String cartao = casas.criar("/api/accounts", casas.casaA,
                "{\"nome\":\"Nubank\",\"tipo\":\"CARTAO\",\"fechamento\":5,\"vencimento\":12}").path("id").asString();
        casas.criar("/api/expenses", casas.casaA, "{\"data\":\"2026-09-20\",\"contaId\":\"" + cartao
                + "\",\"recorrente\":true,\"itens\":[{\"item\":\"Streaming\",\"categoria\":\"Lazer\",\"valor\":30,"
                + "\"tipo\":\"Fixo\"}]}");

        String conta = mockMvc.perform(get("/api/bills").param("mes", "2026-09").with(casas.casaA))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].automatico").value(true))
                .andExpect(jsonPath("$[0].dia").value(20))
                .andExpect(jsonPath("$[0].situacao").value("PAGA"))
                .andExpect(jsonPath("$[0].contaId").value(cartao))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(conta).path(0).path("id").asString();

        mockMvc.perform(post("/api/bills/" + id + "/pay").param("mes", "2026-10").with(casas.casaA))
                .andExpect(status().isCreated());
        // Setembro (20/set) e outubro (20/out) no cartao: faturas de outubro e de novembro.
        mockMvc.perform(get("/api/accounts/" + cartao + "/statement").param("mes", "2026-11").with(casas.casaA))
                .andExpect(jsonPath("$.fatura.total").value(30));
        mockMvc.perform(post("/api/expenses").with(casas.casaA)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-20\",\"recorrente\":true,\"parcelas\":3,"
                                + "\"itens\":[{\"item\":\"TV\",\"valor\":900}]}"))
                .andExpect(status().is(422));
    }

    @Test
    @DisplayName("salario recorrente lanca o mes e aparece so na lista de receitas recorrentes")
    void receitaRecorrente() throws Exception {
        casas.criar("/api/incomes", casas.casaA, "{\"data\":\"2026-09-05\",\"descricao\":\"Salario\","
                + "\"categoria\":\"Salário\",\"valor\":5000,\"recorrente\":true}");

        mockMvc.perform(get("/api/bills").param("mes", "2026-09").with(casas.casaA))
                .andExpect(jsonPath("$", hasSize(0)));
        String lista = mockMvc.perform(get("/api/bills").param("mes", "2026-10").param("tipo", "RECEITA")
                        .with(casas.casaA))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tipo").value("RECEITA"))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/bills/" + json.readTree(lista).path(0).path("id").asString() + "/pay")
                .param("mes", "2026-10").with(casas.casaA)).andExpect(status().isCreated());
        mockMvc.perform(get("/api/incomes").param("mes", "2026-10").with(casas.casaA))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].data").value("2026-10-05"))
                .andExpect(jsonPath("$[0].valor").value(5000));
    }

    @Test
    @DisplayName("apagar a parcela 2 com as proximas deixa so a 1")
    void apagarParcelasSeguintes() throws Exception {
        JsonNode parcelas = casas.criar("/api/expenses", casas.casaA,
                "{\"data\":\"2026-09-10\",\"parcelas\":3,\"itens\":[{\"item\":\"Fone\",\"valor\":90}]}");

        mockMvc.perform(delete("/api/expenses/" + parcelas.path(1).path("id").asString())
                .param("proximas", "true").with(casas.casaA)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/expenses").with(casas.casaA))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].parcela").value(1));
    }

    @Test
    @DisplayName("quem deve so vira receita no Recebi, com a data de hoje, uma vez so")
    void aReceber() throws Exception {
        String id = casas.criar("/api/incomes/receivables", casas.casaA,
                "{\"quem\":\"Joao\",\"descricao\":\"Pizza\",\"valor\":40,\"data\":\"2026-09-01\"}").path("id").asString();
        String mes = YearMonth.now(Meses.BRASILIA).toString();
        mockMvc.perform(get("/api/incomes").param("mes", mes).with(casas.casaA)).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/incomes/receivables").with(casas.casaB)).andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(post("/api/incomes/receivables/" + id + "/receive").with(casas.casaA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Joao · Pizza"))
                .andExpect(jsonPath("$.data").value(LocalDate.now(Meses.BRASILIA).toString()));
        mockMvc.perform(post("/api/incomes/receivables/" + id + "/receive").with(casas.casaA))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/incomes/receivables").with(casas.casaA)).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/incomes").param("mes", mes).with(casas.casaA)).andExpect(jsonPath("$", hasSize(1)));
    }
}
