package com.piggu.identity.api;

import com.piggu.common.error.UnauthorizedException;
import com.piggu.identity.google.GoogleIdTokenVerifier;
import com.piggu.identity.google.GoogleProfile;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login com Google no app: a volta do Google vira um link piggu:// e o app troca pelo ID token. */
@AutoConfigureMockMvc
class LoginNoAppTest extends PostgresIntegrationTest {

    /** Exemplo do PKCE na RFC 7636: verificador e o SHA-256 dele. */
    private static final String VERIFICADOR = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String DESAFIO = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    private static final Pattern LINK = Pattern.compile("piggu://entrar\\?google=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoogleIdTokenVerifier google;

    @Test
    @DisplayName("sem token: a volta do Google abre o app com um codigo, e o codigo vira o ID token uma vez")
    void voltaETroca() throws Exception {
        given(google.verificar("id-token", DESAFIO)).willReturn(
                new GoogleProfile("sub-app", "app@exemplo.test", "Pessoa", "Pessoa", null));

        String pagina = mockMvc.perform(post("/api/auth/google/app")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("Origin", "https://accounts.google.com")
                        .param("id_token", "id-token")
                        .param("state", DESAFIO))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn().getResponse().getContentAsString();

        Matcher link = LINK.matcher(pagina);
        assertThat(link.find()).isTrue();
        String troca = "{\"codigo\":\"" + link.group(1) + "\",\"verificador\":\"" + VERIFICADOR + "\"}";

        mockMvc.perform(post("/api/auth/google/app/troca").contentType(MediaType.APPLICATION_JSON).content(troca))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idToken").value("id-token"));
        mockMvc.perform(post("/api/auth/google/app/troca").contentType(MediaType.APPLICATION_JSON).content(troca))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("cancelado no Google ou token recusado: a pagina so devolve ao app, sem codigo")
    void semCodigo() throws Exception {
        given(google.verificar("falso", DESAFIO)).willThrow(new UnauthorizedException("Login Google invalido para este site."));

        for (String[] campos : new String[][]{{"error", "access_denied"}, {"id_token", "falso"}}) {
            String pagina = mockMvc.perform(post("/api/auth/google/app")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param(campos[0], campos[1])
                            .param("state", DESAFIO))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(pagina).contains("href=\"piggu://entrar\"").doesNotContain("google=");
        }
    }
}
