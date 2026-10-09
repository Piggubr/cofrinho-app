package com.piggu.identity.api;

import com.piggu.testing.PostgresIntegrationTest;
import com.piggu.testing.TokensDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Administracao de contas e preferencias da propria conta, na porta da API. */
@AutoConfigureMockMvc
class PermissoesDaApiTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("a chave publica e aberta; o resto pede token")
    void publicoEProtegido() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk());
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("so o admin lista contas da instalacao")
    void soAdminAdministra() throws Exception {
        mockMvc.perform(get("/api/users").with(TokensDeTeste.titular())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users").with(TokensDeTeste.membro()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users").with(TokensDeTeste.admin())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("membro nao convida, nao remove ninguem nem renomeia a familia")
    void membroNaoCuidaDaFamilia() throws Exception {
        mockMvc.perform(post("/api/family/invites").with(TokensDeTeste.membro())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x@piggu.test\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/family/members/" + java.util.UUID.randomUUID()).with(TokensDeTeste.membro()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/family").with(TokensDeTeste.membro())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Outra\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("convite com e-mail invalido e 400")
    void conviteInvalido() throws Exception {
        mockMvc.perform(post("/api/family/invites").with(TokensDeTeste.titular())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nao-e-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("preferencia com moeda vazia e 400; com moeda inexistente e 422")
    void validaPreferencias() throws Exception {
        mockMvc.perform(put("/api/auth/me/preferences").with(TokensDeTeste.membro())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moeda\":\"\",\"moedaConversao\":\"BRL\",\"mostrarCotacao\":true}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/auth/me/preferences").with(TokensDeTeste.membro())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moeda\":\"XYZ\",\"moedaConversao\":\"BRL\",\"mostrarCotacao\":true}"))
                .andExpect(status().isUnprocessableContent());
    }
}
