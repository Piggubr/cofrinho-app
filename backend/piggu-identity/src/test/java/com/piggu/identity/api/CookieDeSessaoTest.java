package com.piggu.identity.api;

import com.piggu.common.dados.Consentimentos;
import com.piggu.identity.google.GoogleIdTokenVerifier;
import com.piggu.identity.google.GoogleProfile;
import com.piggu.testing.PostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O refresh vive num cookie que o JavaScript nao le; o corpo so leva o access token.
 */
@AutoConfigureMockMvc
class CookieDeSessaoTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoogleIdTokenVerifier google;

    @BeforeEach
    void preparar() {
        given(google.verificar(anyString())).willReturn(new GoogleProfile(
                "sub-cookie", "cookie@exemplo.test", "Pessoa", "Pessoa", null));
    }

    @Test
    @DisplayName("login devolve o refresh so no cookie HttpOnly; Secure; SameSite=Strict; Path=/api/auth")
    void loginPoeOCookie() throws Exception {
        MvcResult login = entrar(true);

        String cookie = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie).startsWith(AuthController.COOKIE + "=")
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth", "Max-Age=2592000");
        assertThat(login.getResponse().getContentAsString())
                .contains("accessToken").doesNotContain("refreshToken").doesNotContain("lembrar");
    }

    @Test
    @DisplayName("sem continuar conectado, o cookie e de sessao do navegador")
    void cookieDeSessao() throws Exception {
        assertThat(entrar(false).getResponse().getHeader(HttpHeaders.SET_COOKIE)).doesNotContain("Max-Age");
    }

    @Test
    @DisplayName("renovar le o cookie, troca por outro e o antigo para de valer; sem cookie e 401")
    void renovarComCookie() throws Exception {
        Cookie primeiro = entrar(true).getResponse().getCookie(AuthController.COOKIE);

        MvcResult renovado = mockMvc.perform(post("/api/auth/refresh").cookie(primeiro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie segundo = renovado.getResponse().getCookie(AuthController.COOKIE);
        assertThat(segundo.getValue()).isNotEqualTo(primeiro.getValue());
        assertThat(renovado.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=2592000");

        mockMvc.perform(post("/api/auth/refresh").cookie(primeiro)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("sair encerra a sessao e apaga o cookie")
    void sairApagaOCookie() throws Exception {
        Cookie sessao = entrar(true).getResponse().getCookie(AuthController.COOKIE);

        MvcResult saida = mockMvc.perform(post("/api/auth/logout").cookie(sessao))
                .andExpect(status().isNoContent()).andReturn();

        assertThat(saida.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        mockMvc.perform(post("/api/auth/refresh").cookie(sessao)).andExpect(status().isUnauthorized());
    }

    private MvcResult entrar(boolean lembrar) throws Exception {
        return mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"x\",\"versaoDosTermos\":\"" + Consentimentos.VERSAO_DO_AVISO
                                + "\",\"lembrar\":" + lembrar + "}"))
                .andExpect(status().isOk())
                .andReturn();
    }
}
