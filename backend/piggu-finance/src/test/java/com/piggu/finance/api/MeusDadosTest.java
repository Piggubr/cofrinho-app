package com.piggu.finance.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.piggu.common.security.PigguRole;
import com.piggu.testing.DuasFamilias;
import com.piggu.testing.PostgresIntegrationTest;
import com.piggu.testing.TokensDeTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exportar e apagar os dados de uma pessoa no financeiro (LGPD art. 18).
 *
 * <p>Familia com titular e membro, cada um com um gasto, e uma familia vizinha que
 * nunca pode ser tocada.</p>
 */
@AutoConfigureMockMvc
class MeusDadosTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private final UUID familia = UUID.randomUUID();
    private final String emailTitular = "titular-" + familia + "@piggu.test";
    private final String emailMembro = "membro-" + familia + "@piggu.test";
    private final RequestPostProcessor titular = TokensDeTeste.da(familia, emailTitular, PigguRole.TITULAR);
    private final RequestPostProcessor membro = TokensDeTeste.da(familia, emailMembro, PigguRole.MEMBRO);
    private DuasFamilias vizinhas;

    @BeforeEach
    void preparar() throws Exception {
        vizinhas = new DuasFamilias(mockMvc, json);
        vizinhas.criar("/api/expenses", titular, gasto("Mercado do titular"));
        vizinhas.criar("/api/piggy-bank/deposits", membro, "{\"data\":\"2026-09-01\",\"valor\":30}");
        vizinhas.criar("/api/expenses", vizinhas.casaA, gasto("Da vizinha"));
    }

    @Test
    @DisplayName("titular exporta a familia inteira; membro, so o que lancou")
    void exportar() throws Exception {
        mockMvc.perform(get("/api/meus-dados").with(titular))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenses.length()").value(1))
                .andExpect(jsonPath("$.piggy_deposits.length()").value(1))
                .andExpect(jsonPath("$.expenses[0].item").value("Mercado do titular"));

        mockMvc.perform(get("/api/meus-dados").with(membro))
                .andExpect(jsonPath("$.expenses.length()").value(0))
                .andExpect(jsonPath("$.piggy_deposits.length()").value(1));
    }

    @Test
    @DisplayName("token comum nao apaga nada, nem o da propria pessoa")
    void tokenComumNaoApaga() throws Exception {
        mockMvc.perform(delete("/api/meus-dados").with(titular)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/expenses").with(titular)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("quando a pessoa sai e a familia fica, o que ela lancou fica anonimizado")
    void pessoaSaiFamiliaFica() throws Exception {
        mockMvc.perform(delete("/api/meus-dados")
                        .with(TokensDeTeste.exclusao(familia, emailMembro, PigguRole.MEMBRO, "PESSOA")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/piggy-bank").with(titular))
                .andExpect(jsonPath("$.depositos.length()").value(1))
                .andExpect(jsonPath("$.depositos[0].usuario").value("conta-excluida"));
        mockMvc.perform(get("/api/expenses").with(titular))
                .andExpect(jsonPath("$[0].usuario").value(emailTitular));
    }

    @Test
    @DisplayName("quando a ultima pessoa sai, a familia inteira sai, e a vizinha nao perde nada")
    void familiaInteiraSai() throws Exception {
        mockMvc.perform(delete("/api/meus-dados")
                        .with(TokensDeTeste.exclusao(familia, emailTitular, PigguRole.TITULAR, "FAMILIA")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/meus-dados").with(titular))
                .andExpect(jsonPath("$.expenses.length()").value(0))
                .andExpect(jsonPath("$.piggy_deposits.length()").value(0));
        mockMvc.perform(get("/api/expenses").with(vizinhas.casaA)).andExpect(jsonPath("$.length()").value(1));
    }

    private static String gasto(String item) {
        return "{\"data\":\"2026-09-10\",\"itens\":[{\"item\":\"" + item + "\",\"categoria\":\"Mercado\",\"valor\":10}]}";
    }
}
