package com.piggu.finance.api;

import com.piggu.testing.DuasFamilias;
import com.piggu.testing.PostgresIntegrationTest;
import com.piggu.testing.TokensDeTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Receitas e o card do mes: receitas - gastos = sobra; sobra / receitas = poupanca. */
@AutoConfigureMockMvc
class ReceitasEResumoTest extends PostgresIntegrationTest {

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
    @DisplayName("o mes fecha a conta: 5000 de receita, 1250 de gasto, sobra 3750, poupanca 75%")
    void resumoDoMes() throws Exception {
        casas.criar("/api/incomes", casas.casaA, "{\"data\":\"2026-09-05\",\"descricao\":\"Salario\",\"categoria\":\"salário\",\"valor\":5000}");
        casas.criar("/api/expenses", casas.casaA,
                "{\"data\":\"2026-09-10\",\"itens\":[{\"item\":\"Aluguel\",\"categoria\":\"Casa\",\"valor\":1250}]}");
        casas.criar("/api/incomes", casas.casaA, "{\"data\":\"2026-08-31\",\"descricao\":\"Outro mes\",\"valor\":99}");

        mockMvc.perform(get("/api/reports/month").param("mes", "2026-09").with(casas.casaA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receitas").value(5000))
                .andExpect(jsonPath("$.gastos").value(1250))
                .andExpect(jsonPath("$.sobra").value(3750))
                .andExpect(jsonPath("$.taxaDePoupanca").value(75.0))
                .andExpect(jsonPath("$.gastosMesAnterior").value(0))
                .andExpect(jsonPath("$.variacao").doesNotExist())
                .andExpect(jsonPath("$.porCategoria[0].total").value(1250));
        mockMvc.perform(get("/api/incomes").param("mes", "2026-09").with(casas.casaA))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].categoria").value("Salário"));

        mockMvc.perform(get("/api/reports/month").param("mes", "2026-09").with(casas.casaB))
                .andExpect(jsonPath("$.receitas").value(0))
                .andExpect(jsonPath("$.taxaDePoupanca").doesNotExist());
    }

    @Test
    @DisplayName("receita de outra familia nao e apagada; mes invalido e 400")
    void isolamentoEMesInvalido() throws Exception {
        String id = casas.criar("/api/incomes", casas.casaA, "{\"data\":\"2026-09-05\",\"descricao\":\"Bonus\",\"valor\":10}")
                .path("id").asString();

        mockMvc.perform(delete("/api/incomes/" + id).with(casas.casaB)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/reports/month").param("mes", "setembro").with(casas.casaA))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("mes contra o anterior por categoria; o ano e Premium e soma mes a mes")
    void comparacaoEAno() throws Exception {
        casas.criar("/api/expenses", casas.casaA,
                "{\"data\":\"2025-03-10\",\"itens\":[{\"item\":\"Feira\",\"categoria\":\"Mercado\",\"valor\":200}]}");
        casas.criar("/api/expenses", casas.casaA,
                "{\"data\":\"2025-04-10\",\"itens\":[{\"item\":\"Feira\",\"categoria\":\"Mercado\",\"valor\":300}]}");
        casas.criar("/api/incomes", casas.casaA, "{\"data\":\"2025-04-05\",\"descricao\":\"Salario\",\"valor\":1000}");

        mockMvc.perform(get("/api/reports/month").param("mes", "2025-04").with(casas.casaA))
                .andExpect(jsonPath("$.gastosMesAnterior").value(200))
                .andExpect(jsonPath("$.variacao").value(50.0))
                .andExpect(jsonPath("$.projecaoDeGastos").doesNotExist())
                .andExpect(jsonPath("$.porCategoria[0].anterior").value(200));

        mockMvc.perform(get("/api/reports/year").param("ano", "2025").with(casas.casaA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meses.length()").value(12))
                .andExpect(jsonPath("$.meses[3].sobra").value(700))
                .andExpect(jsonPath("$.gastos").value(500))
                .andExpect(jsonPath("$.porCategoria[0].total").value(500));
        mockMvc.perform(get("/api/reports/year").param("ano", "2025").with(TokensDeTeste.titularGratuita()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PLANO_PREMIUM"));
        mockMvc.perform(get("/api/reports/year").param("ano", "99999").with(casas.casaA))
                .andExpect(status().isBadRequest());
    }
}
