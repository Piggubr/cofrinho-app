package com.piggu.finance.api;

import com.piggu.common.security.PigguRole;
import com.piggu.testing.PostgresIntegrationTest;
import com.piggu.testing.TokensDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Receitas: categoria fechada, edicao, exclusao e o rastro de cada uma no historico. */
@AutoConfigureMockMvc
class ReceitasTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private final RequestPostProcessor titular =
            TokensDeTeste.da(UUID.randomUUID(), "titular@receitas.test", PigguRole.TITULAR);

    @Test
    @DisplayName("as categorias sao fixas; texto livre vira Outros e a caixa nao importa")
    void categorias() throws Exception {
        mockMvc.perform(get("/api/incomes/categories").with(titular))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Salário"))
                .andExpect(jsonPath("$[4]").value("Outros"));

        criar("{\"data\":\"2026-09-01\",\"descricao\":\"Freela\",\"categoria\":\"EXTRA\",\"valor\":300}");
        criar("{\"data\":\"2026-09-02\",\"descricao\":\"Pix da tia\",\"categoria\":\"Presente\",\"valor\":50}");

        mockMvc.perform(get("/api/incomes").param("mes", "2026-09").with(titular))
                .andExpect(jsonPath("$[*].categoria", contains("Outros", "Extra")));
    }

    @Test
    @DisplayName("editar e apagar ficam no historico com o antes e o depois")
    void edicaoEExclusao() throws Exception {
        String id = criar("{\"data\":\"2026-09-05\",\"descricao\":\"Salario\",\"categoria\":\"Salário\",\"valor\":5000}");

        mockMvc.perform(put("/api/incomes/" + id).with(titular).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-10-05\",\"descricao\":\"Salario de outubro\",\"categoria\":\"Salário\",\"valor\":5200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Salario de outubro"))
                .andExpect(jsonPath("$.valor").value(5200));
        mockMvc.perform(get("/api/incomes").param("mes", "2026-09").with(titular))
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(delete("/api/incomes/" + id).with(titular)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/incomes/" + id).with(titular)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/incomes").param("mes", "2026-10").with(titular))
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/history").param("entidade", "receita").with(titular))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].acao", contains("APAGOU", "EDITOU", "CRIOU")));
    }

    @Test
    @DisplayName("valor zero, sem descricao ou sem data e recusado")
    void validacao() throws Exception {
        for (String corpo : new String[]{
                "{\"data\":\"2026-09-01\",\"descricao\":\"X\",\"valor\":0}",
                "{\"data\":\"2026-09-01\",\"descricao\":\" \",\"valor\":10}",
                "{\"descricao\":\"X\",\"valor\":10}"}) {
            mockMvc.perform(post("/api/incomes").with(titular).contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest());
        }
    }

    private String criar(String corpo) throws Exception {
        String resposta = mockMvc.perform(post("/api/incomes").with(titular)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).path("id").asString();
    }
}
