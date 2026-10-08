package com.piggu.lifestyle.api;

import com.piggu.testing.PostgresIntegrationTest;
import com.piggu.testing.TokensDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lugares, filmes e compras sao so de ADMIN e TITULAR, como no Apps Script. */
@AutoConfigureMockMvc
class PermissoesDaApiTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("sem token nada responde")
    void semToken() throws Exception {
        mockMvc.perform(get("/api/places")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/movies")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/shopping/items")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("membro nao alcanca lugares, filmes nem compras")
    void membroNaoAlcanca() throws Exception {
        mockMvc.perform(get("/api/places").with(TokensDeTeste.membro())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/movies").with(TokensDeTeste.membro())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/shopping/items").with(TokensDeTeste.membro())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("titular alcanca as tres listas")
    void titularAlcanca() throws Exception {
        mockMvc.perform(get("/api/places").with(TokensDeTeste.titular())).andExpect(status().isOk());
        mockMvc.perform(get("/api/movies").with(TokensDeTeste.titular())).andExpect(status().isOk());
        mockMvc.perform(get("/api/shopping/items").with(TokensDeTeste.titular())).andExpect(status().isOk());
    }
}
