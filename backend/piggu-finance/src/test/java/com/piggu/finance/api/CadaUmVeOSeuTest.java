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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cada pessoa ve e mexe so no que lancou; o titular ve tambem o dos menores; o gasto
 * dividido aparece para quem divide, mas so quem lancou muda.
 */
@AutoConfigureMockMvc
class CadaUmVeOSeuTest extends PostgresIntegrationTest {

    private static final String TITULAR = TokensDeTeste.EMAIL_TITULAR;
    private static final String MENOR = "menor.ve@piggu.test";
    private static final String ADULTO = "adulto.ve@piggu.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Test
    @DisplayName("membro, adulto e titular veem so o deles; o titular ve tambem o do menor")
    void cadaUmVeOSeu() throws Exception {
        UUID casa = UUID.randomUUID();
        RequestPostProcessor titular = TokensDeTeste.da(casa, TITULAR, PigguRole.TITULAR);
        RequestPostProcessor menor = TokensDeTeste.da(casa, MENOR, PigguRole.MEMBRO);
        RequestPostProcessor adulto = TokensDeTeste.da(casa, ADULTO, PigguRole.MEMBRO);
        lancar(titular, "Do titular");
        String doMenor = lancar(menor, "Do menor");
        String doAdulto = lancar(adulto, "Do adulto");

        mockMvc.perform(get("/api/expenses").with(menor))
                .andExpect(jsonPath("$[*].item").value(containsInAnyOrder("Do menor")));
        mockMvc.perform(get("/api/expenses").with(adulto))
                .andExpect(jsonPath("$[*].item").value(containsInAnyOrder("Do adulto")));
        mockMvc.perform(get("/api/expenses").with(titular))
                .andExpect(jsonPath("$[*].item").value(containsInAnyOrder("Do titular")));
        mockMvc.perform(get("/api/expenses").with(TokensDeTeste.responsavelVendo(casa, MENOR)))
                .andExpect(jsonPath("$[*].item").value(containsInAnyOrder("Do titular", "Do menor")));

        // Nem pelo id: o filtro vale tambem no findById.
        mockMvc.perform(delete("/api/expenses/" + doAdulto).with(menor)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/expenses/" + doAdulto).with(TokensDeTeste.responsavelVendo(casa, MENOR)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/expenses/" + doMenor).with(TokensDeTeste.responsavelVendo(casa, MENOR)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("cada um tem o proprio cofrinho; o do titular soma o do menor")
    void cofrinhoDeCadaUm() throws Exception {
        UUID casa = UUID.randomUUID();
        depositar(TokensDeTeste.da(casa, MENOR, PigguRole.MEMBRO), 100);
        depositar(TokensDeTeste.da(casa, TITULAR, PigguRole.TITULAR), 50);

        mockMvc.perform(get("/api/piggy-bank").with(TokensDeTeste.da(casa, MENOR, PigguRole.MEMBRO)))
                .andExpect(jsonPath("$.totalDepositos").value(100));
        mockMvc.perform(get("/api/piggy-bank").with(TokensDeTeste.da(casa, TITULAR, PigguRole.TITULAR)))
                .andExpect(jsonPath("$.totalDepositos").value(50));
        mockMvc.perform(get("/api/piggy-bank").with(TokensDeTeste.responsavelVendo(casa, MENOR)))
                .andExpect(jsonPath("$.totalDepositos").value(150));
    }

    private void depositar(RequestPostProcessor quem, int valor) throws Exception {
        mockMvc.perform(post("/api/piggy-bank/deposits").with(quem).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-01\",\"valor\":" + valor + "}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("gasto dividido aparece para quem divide, mas so quem lancou muda")

    void gastoDividido() throws Exception {
        UUID casa = UUID.randomUUID();
        RequestPostProcessor quemPagou = TokensDeTeste.da(casa, TITULAR, PigguRole.TITULAR);
        RequestPostProcessor quemDivide = TokensDeTeste.da(casa, ADULTO, PigguRole.MEMBRO);
        mockMvc.perform(post("/api/expenses").with(quemPagou).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-10\",\"dividirCom\":[\"" + TokensDeTeste.idDe(TITULAR) + "\",\""
                                + TokensDeTeste.idDe(ADULTO) + "\"],\"itens\":[{\"item\":\"Jantar\",\"valor\":100}]}"))
                .andExpect(status().isCreated());

        String id = json.readTree(mockMvc.perform(get("/api/expenses").with(quemDivide))
                .andExpect(jsonPath("$[*].item").value(containsInAnyOrder("Jantar")))
                .andReturn().getResponse().getContentAsString()).get(0).path("id").asString();
        mockMvc.perform(put("/api/expenses/" + id).with(quemDivide).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"item\":\"Outro\",\"categoria\":\"Lazer\",\"valor\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("quem muda de familia leva os lancamentos; a divisao com quem ficou se desfaz")
    void mudarDeFamiliaLevaOsDados() throws Exception {
        UUID antiga = UUID.randomUUID();
        UUID nova = UUID.randomUUID();
        RequestPostProcessor titular = TokensDeTeste.da(antiga, TITULAR, PigguRole.TITULAR);
        lancar(titular, "Fica");
        mockMvc.perform(post("/api/expenses").with(TokensDeTeste.da(antiga, ADULTO, PigguRole.MEMBRO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-10\",\"dividirCom\":[\"" + TokensDeTeste.idDe(TITULAR) + "\",\""
                                + TokensDeTeste.idDe(ADULTO) + "\"],\"itens\":[{\"item\":\"Vai\",\"valor\":100}]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/meus-dados").with(TokensDeTeste.mudanca(antiga, ADULTO, PigguRole.MEMBRO, nova)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/expenses").with(titular))
                .andExpect(jsonPath("$[*].item").value(containsInAnyOrder("Fica")));
        mockMvc.perform(get("/api/expenses").with(TokensDeTeste.da(nova, ADULTO, PigguRole.TITULAR)))
                .andExpect(jsonPath("$[*].item").value(containsInAnyOrder("Vai")));
        mockMvc.perform(get("/api/expenses/splits").param("mes", "2026-09").with(titular))
                .andExpect(jsonPath("$.length()").value(0));
    }

    private String lancar(RequestPostProcessor quem, String item) throws Exception {

        String resposta = mockMvc.perform(post("/api/expenses").with(quem).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-10\",\"itens\":[{\"item\":\"" + item + "\",\"valor\":10}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).get(0).path("id").asString();
    }
}
