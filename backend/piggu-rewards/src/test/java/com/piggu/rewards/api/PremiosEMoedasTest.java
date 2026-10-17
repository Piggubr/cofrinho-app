package com.piggu.rewards.api;

import com.piggu.common.security.PigguRole;
import com.piggu.testing.PostgresIntegrationTest;
import com.piggu.testing.TokensDeTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fofocoins e premios pela API: ajuste, cadastro, desativacao e resgate. */
@AutoConfigureMockMvc
class PremiosEMoedasTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    /** Uma familia nova por teste: o saldo de uma nao vaza para a outra. */
    private UUID familia;
    private RequestPostProcessor titular;

    @BeforeEach
    void preparar() {
        familia = UUID.randomUUID();
        titular = TokensDeTeste.da(familia, "titular-" + familia + "@piggu.test", PigguRole.TITULAR);
    }

    @Test
    @DisplayName("ajuste credita e debita com motivo; saldo nunca fica negativo nem aceita zero")
    void ajusteDeMoedas() throws Exception {
        ajustar(titular, 100, "Semana sem delivery").andExpect(status().isOk()).andExpect(jsonPath("$.saldo").value(100));
        ajustar(titular, -30, "Correcao").andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(70))
                .andExpect(jsonPath("$.historico.length()").value(2))
                .andExpect(jsonPath("$.historico[0].valor").value(-30))
                .andExpect(jsonPath("$.historico[0].motivo").value("Correcao"));

        ajustar(titular, -71, "Demais").andExpect(status().isUnprocessableContent());
        ajustar(titular, 0, "Nada").andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/coins/adjustments").with(titular).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":10,\"motivo\":\" \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/coins").with(titular)).andExpect(jsonPath("$.saldo").value(70));
    }

    @Test
    @DisplayName("membro ve o saldo mas nao ajusta nem cadastra premio")
    void membro() throws Exception {
        RequestPostProcessor membro = TokensDeTeste.da(familia, "membro-" + familia + "@piggu.test", PigguRole.MEMBRO);
        mockMvc.perform(get("/api/coins").with(membro)).andExpect(status().isOk());
        ajustar(membro, 10, "Eu mesmo").andExpect(status().isForbidden());
        premio(membro, "Cinema", 50).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/prizes").with(membro)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("premio: cadastra ativo, edita, e apagar so desativa (some da lista, fica no historico)")
    void cadastroDePremio() throws Exception {
        String id = id(premio(titular, "Cinema", 80).andExpect(status().isCreated())
                .andExpect(jsonPath("$.ativo").value(true)));

        mockMvc.perform(put("/api/prizes/" + id).with(titular).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Cinema com pipoca\",\"descricao\":\"Sessao a escolha\",\"preco\":90}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Cinema com pipoca"))
                .andExpect(jsonPath("$.preco").value(90));

        mockMvc.perform(delete("/api/prizes/" + id).with(titular)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/prizes").with(titular)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/prizes").param("todos", "true").with(titular))
                .andExpect(jsonPath("$[0].ativo").value(false));

        mockMvc.perform(put("/api/prizes/" + UUID.randomUUID()).with(titular).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"x\",\"preco\":1}"))
                .andExpect(status().isNotFound());
        premio(titular, "", 10).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("resgate debita o preco e entra no historico; sem saldo ou premio inativo, recusa")
    void resgate() throws Exception {
        String id = id(premio(titular, "Jantar fora", 150));
        mockMvc.perform(post("/api/prizes/" + id + "/redemptions").with(titular))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.erro").value("Voce ainda nao possui Fofocoins suficientes."));

        ajustar(titular, 200, "Mes no azul");
        mockMvc.perform(post("/api/prizes/" + id + "/redemptions").with(titular))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.premio").value("Jantar fora"))
                .andExpect(jsonPath("$.saldo").value(50));
        mockMvc.perform(get("/api/coins").with(titular))
                .andExpect(jsonPath("$.saldo").value(50))
                .andExpect(jsonPath("$.historico[0].motivo").value("Resgate: Jantar fora"));
        mockMvc.perform(get("/api/prizes/redemptions").with(titular))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].preco").value(150));

        mockMvc.perform(delete("/api/prizes/" + id).with(titular));
        ajustar(titular, 500, "Mais");
        mockMvc.perform(post("/api/prizes/" + id + "/redemptions").with(titular))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.erro").value("Este premio nao esta disponivel."));

        RequestPostProcessor outra = TokensDeTeste.da(UUID.randomUUID(), "outra@piggu.test", PigguRole.TITULAR);
        mockMvc.perform(post("/api/prizes/" + id + "/redemptions").with(outra)).andExpect(status().isNotFound());
    }

    private ResultActions ajustar(RequestPostProcessor quem, int valor, String motivo) throws Exception {
        return mockMvc.perform(post("/api/coins/adjustments").with(quem).contentType(MediaType.APPLICATION_JSON)
                .content("{\"valor\":%d,\"motivo\":\"%s\"}".formatted(valor, motivo)));
    }

    private ResultActions premio(RequestPostProcessor quem, String nome, int preco) throws Exception {
        return mockMvc.perform(post("/api/prizes").with(quem).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"%s\",\"preco\":%d}".formatted(nome, preco)));
    }

    private String id(ResultActions resposta) throws Exception {
        return json.readTree(resposta.andReturn().getResponse().getContentAsString()).path("id").asString();
    }
}
