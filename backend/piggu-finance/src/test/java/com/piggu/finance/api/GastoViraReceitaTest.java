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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gasto lancado por engano vira receita de uma vez (D-199). */
@AutoConfigureMockMvc
class GastoViraReceitaTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Test
    @DisplayName("o gasto sai, a receita entra com a mesma data, descricao, valor e compartilhamento")
    void viraReceita() throws Exception {
        UUID casa = UUID.randomUUID();
        RequestPostProcessor ela = TokensDeTeste.da(casa, "ela.vira@piggu.test", PigguRole.TITULAR);
        String id = lancar(ela, "{\"data\":\"2026-09-10\",\"compartilhado\":true,"
                + "\"itens\":[{\"item\":\"Pix do freela\",\"valor\":350}]}");

        mockMvc.perform(post("/api/expenses/" + id + "/virar-receita").with(ela))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data").value("2026-09-10"))
                .andExpect(jsonPath("$.descricao").value("Pix do freela"))
                .andExpect(jsonPath("$.categoria").value("Outros"))
                .andExpect(jsonPath("$.valor").value(350))
                .andExpect(jsonPath("$.compartilhado").value(true));

        mockMvc.perform(get("/api/expenses").param("mes", "2026-09").with(ela))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/incomes").param("mes", "2026-09").with(ela))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(post("/api/expenses/" + id + "/virar-receita").with(ela))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("so quem lancou transforma; parcelado nao vira receita")
    void regras() throws Exception {
        UUID casa = UUID.randomUUID();
        RequestPostProcessor ela = TokensDeTeste.da(casa, "ela.regras@piggu.test", PigguRole.TITULAR);
        RequestPostProcessor ele = TokensDeTeste.da(casa, "ele.regras@piggu.test", PigguRole.PARCEIRO);
        String aluguel = lancar(ela, "{\"data\":\"2026-09-10\",\"compartilhado\":true,"
                + "\"itens\":[{\"item\":\"Aluguel\",\"valor\":1500}]}");
        String tv = lancar(ela, "{\"data\":\"2026-09-10\",\"parcelas\":3,\"itens\":[{\"item\":\"TV\",\"valor\":900}]}");

        mockMvc.perform(post("/api/expenses/" + aluguel + "/virar-receita").with(ele))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/expenses/" + tv + "/virar-receita").with(ela))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/expenses").param("mes", "2026-09").with(ela))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/incomes").param("mes", "2026-09").with(ela))
                .andExpect(jsonPath("$.length()").value(0));
    }

    private String lancar(RequestPostProcessor quem, String corpo) throws Exception {
        String resposta = mockMvc.perform(post("/api/expenses").with(quem).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).path(0).path("id").asString();
    }
}
