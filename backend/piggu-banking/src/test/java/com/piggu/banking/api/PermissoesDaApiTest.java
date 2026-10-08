package com.piggu.banking.api;

import com.piggu.banking.integration.PluggyClient;
import com.piggu.common.dados.Consentimentos;
import com.piggu.testing.PostgresIntegrationTest;
import com.piggu.testing.TokensDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Quem alcanca as rotas de banco. Saldo bancario fica fora do alcance do perfil MEMBRO. */
@AutoConfigureMockMvc
class PermissoesDaApiTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PluggyClient pluggy;

    @Test
    @DisplayName("sem token nenhuma rota de banco responde")
    void semTokenNaoPassa() throws Exception {
        mockMvc.perform(get("/api/banking/accounts")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/banking/connect-token")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("membro nao ve saldo bancario")
    void membroNaoAlcanca() throws Exception {
        mockMvc.perform(get("/api/banking/accounts").with(TokensDeTeste.membro()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/banking/connect-token").with(TokensDeTeste.membro()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("titular lista as contas e pede o token do widget")
    void titularAlcanca() throws Exception {
        when(pluggy.criarConnectToken(anyString())).thenReturn("token-do-widget");

        mockMvc.perform(get("/api/banking/accounts").with(TokensDeTeste.titular()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/banking/connect-token").with(TokensDeTeste.titular()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CONSENTIMENTO_NECESSARIO"));
        mockMvc.perform(post("/api/banking/connect-token").with(TokensDeTeste.titular())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"autorizo\":true,\"versaoDoAviso\":\"1999-01-01\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/banking/connect-token").with(TokensDeTeste.titular())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"autorizo\":true,\"versaoDoAviso\":\"" + Consentimentos.VERSAO_DO_AVISO + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token-do-widget"))
                .andExpect(jsonPath("$.sandbox").value(false));
    }

    @Test
    @DisplayName("sem credenciais da Pluggy o status diz desligado")
    void statusDesligadoSemCredenciais() throws Exception {
        mockMvc.perform(get("/api/banking/status").with(TokensDeTeste.titular()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitado").value(false));
    }

    @Test
    @DisplayName("id de item com caractere estranho e recusado antes de ir a Pluggy")
    void itemInvalido() throws Exception {
        mockMvc.perform(post("/api/banking/items")
                        .with(TokensDeTeste.titular())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":\"../auth\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("no gratuito conectar e sincronizar pedem Premium, mas as contas ja salvas seguem visiveis")
    void gratuitoNaoConecta() throws Exception {
        mockMvc.perform(post("/api/banking/connect-token").with(TokensDeTeste.titularGratuita()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PLANO_PREMIUM"));
        mockMvc.perform(post("/api/banking/sync").with(TokensDeTeste.titularGratuita()))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/banking/accounts").with(TokensDeTeste.titularGratuita()))
                .andExpect(status().isOk());
        // Desconectar nunca pede Premium: aqui so nao acha o banco.
        mockMvc.perform(delete("/api/banking/connections/" + java.util.UUID.randomUUID())
                        .with(TokensDeTeste.titularGratuita()))
                .andExpect(status().isNotFound());
    }
}
