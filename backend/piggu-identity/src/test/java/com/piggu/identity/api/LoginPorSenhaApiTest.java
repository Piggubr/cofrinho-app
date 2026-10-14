package com.piggu.identity.api;

import com.piggu.common.dados.Consentimentos;
import com.piggu.identity.domain.HouseholdInviteRepository;
import com.piggu.identity.domain.RefreshSessionRepository;
import com.piggu.identity.domain.UserAccountRepository;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cadastro e entrada por senha na porta da API: rotas abertas, cookie e erros legiveis. */
@AutoConfigureMockMvc
class LoginPorSenhaApiTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository usuarios;

    @Autowired
    private RefreshSessionRepository sessoes;

    @Autowired
    private HouseholdInviteRepository convites;

    @BeforeEach
    void limpar() {
        // Tudo: o CPF do cadastro abaixo pode estar numa conta deixada por outro teste.
        sessoes.deleteAll();
        convites.deleteAll();
        usuarios.deleteAll();
    }

    @Test
    @DisplayName("cadastro responde 202 sem sessao (igual para e-mail repetido); a entrada devolve o refresh so no cookie")
    void cadastroEEntrada() throws Exception {
        mockMvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Api\",\"email\":\"api@exemplo.test\",\"senha\":\"cofrinho123\","
                                + "\"pais\":\"BR\",\"documento\":\"111.444.777-35\",\"telefone\":\"11999998888\",\"versaoDosTermos\":\"" + Consentimentos.VERSAO_DO_AVISO + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        String entrada = mockMvc.perform(post("/api/auth/entrar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"api@exemplo.test\",\"senha\":\"cofrinho123\",\"lembrar\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(entrada).startsWith(AuthController.COOKIE + "=").contains("HttpOnly", "Path=/api/auth");

        mockMvc.perform(post("/api/auth/entrar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"api@exemplo.test\",\"senha\":\"errada1234\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value(org.hamcrest.Matchers.startsWith("E-mail ou senha incorretos.")));

        mockMvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Api\",\"email\":\"api@exemplo.test\",\"senha\":\"cofrinho123\","
                                + "\"pais\":\"BR\",\"documento\":\"111.444.777-35\",\"telefone\":\"11999998888\",\"versaoDosTermos\":\"" + Consentimentos.VERSAO_DO_AVISO + "\"}"))
                .andExpect(status().isAccepted());
        assertThat(usuarios.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("e-mail invalido e recusado antes de chegar ao servico")
    void emailInvalido() throws Exception {
        mockMvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Api\",\"email\":\"nao-e-email\",\"senha\":\"cofrinho123\"}"))
                .andExpect(status().is4xxClientError());
        assertThat(usuarios.findByEmail("nao-e-email")).isEmpty();
    }

    @Test
    @DisplayName("informar o pais pede login")
    void paisPedeToken() throws Exception {
        mockMvc.perform(put("/api/me/pais").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pais\":\"PT\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("criar ou trocar a senha pede login")
    void definirSenhaPedeToken() throws Exception {
        mockMvc.perform(put("/api/auth/me/senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"novaSenha\":\"cofrinho123\"}"))
                .andExpect(status().isUnauthorized());
    }
}
