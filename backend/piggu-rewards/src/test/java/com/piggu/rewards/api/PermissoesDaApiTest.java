package com.piggu.rewards.api;

import com.piggu.testing.PostgresIntegrationTest;
import com.piggu.testing.TokensDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quem mexe em Fofocoins e premios.
 *
 * <p>Na familia, o titular (ou o admin, na propria familia) credita, debita, cria
 * premios e resgata; o membro nao mexe em nada disso.</p>
 */
@AutoConfigureMockMvc
class PermissoesDaApiTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("sem token nada responde")
    void semToken() throws Exception {
        mockMvc.perform(get("/api/coins")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/prizes")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("titular e admin ajustam Fofocoins e criam premio; membro nao")
    void soTitularAjusta() throws Exception {
        String ajuste = "{\"valor\":10,\"motivo\":\"teste\"}";
        mockMvc.perform(post("/api/coins/adjustments").with(TokensDeTeste.membro())
                        .contentType(MediaType.APPLICATION_JSON).content(ajuste))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/coins/adjustments").with(TokensDeTeste.titular())
                        .contentType(MediaType.APPLICATION_JSON).content(ajuste))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/coins/adjustments").with(TokensDeTeste.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(ajuste))
                .andExpect(status().isOk());

        String premio = "{\"nome\":\"Sorvete\",\"preco\":5}";
        mockMvc.perform(post("/api/prizes").with(TokensDeTeste.membro())
                        .contentType(MediaType.APPLICATION_JSON).content(premio))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/prizes").with(TokensDeTeste.titular())
                        .contentType(MediaType.APPLICATION_JSON).content(premio))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("membro nao resgata premio nem ve resgates")
    void membroNaoResgata() throws Exception {
        mockMvc.perform(post("/api/prizes/" + UUID.randomUUID() + "/redemptions").with(TokensDeTeste.membro()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/prizes/redemptions").with(TokensDeTeste.membro()))
                .andExpect(status().isForbidden());
    }
}
