package com.piggu.identity.api;

import com.jayway.jsonpath.JsonPath;
import com.piggu.common.dados.Consentimentos;
import com.piggu.identity.google.GoogleIdTokenVerifier;
import com.piggu.identity.google.GoogleProfile;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Quem entra pelo Google completa o mesmo cadastro do e-mail antes de usar os dados (D-088). */
@AutoConfigureMockMvc
class CadastroPeloGoogleTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoogleIdTokenVerifier google;

    @BeforeEach
    void preparar() {
        given(google.verificar(anyString())).willReturn(new GoogleProfile(
                "sub-cadastro", "cadastro@exemplo.test", "Rita Lima", "Rita", null));
    }

    @Test
    @DisplayName("conta nova do Google vem com cadastro incompleto e o token barrado ate informar nascimento, CPF e celular")
    void completaOCadastro() throws Exception {
        String login = entrar().andExpect(jsonPath("$.usuario.cadastroCompleto").value(false))
                .andExpect(jsonPath("$.usuario.celularInformado").value(false))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(login, "$.accessToken");
        assertThat(claims(token)).contains("\"cadastro\":false");

        sobreVoce(token, "{\"apelido\":\"Rita\",\"nascimento\":\"1990-05-10\",\"telefone\":\"11999998888\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CPF_NECESSARIO"));
        sobreVoce(token, "{\"apelido\":\"Rita\",\"nascimento\":\"1990-05-10\",\"cpf\":\"529.982.247-25\"}")
                .andExpect(status().isUnprocessableContent());
        sobreVoce(token, "{\"apelido\":\"Rita\",\"nascimento\":\"1990-05-10\",\"cpf\":\"529.982.247-25\","
                + "\"telefone\":\"11999998888\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpfInformado").value(true))
                .andExpect(jsonPath("$.celularInformado").value(true))
                .andExpect(jsonPath("$.cadastroCompleto").value(true));

        String depois = entrar().andExpect(jsonPath("$.usuario.cadastroCompleto").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(claims(JsonPath.read(depois, "$.accessToken"))).doesNotContain("\"cadastro\"");
        // Completo, o apelido continua mudando sem pedir o resto de novo.
        sobreVoce(JsonPath.read(depois, "$.accessToken"), "{\"apelido\":\"Ritinha\"}").andExpect(status().isOk());
    }

    private ResultActions entrar() throws Exception {
        return mockMvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"x\",\"versaoDosTermos\":\"" + Consentimentos.VERSAO_DO_AVISO + "\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions sobreVoce(String token, String corpo) throws Exception {
        return mockMvc.perform(put("/api/me/sobre-voce").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private static String claims(String token) {
        return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
    }
}
