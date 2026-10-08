package com.piggu.finance.api;

import com.piggu.testing.PostgresIntegrationTest;
import com.piggu.testing.TokensDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regras de perfil na porta da API.
 *
 * <p>Os testes de servico cobrem o calculo; estes cobrem quem tem permissao de
 * chegar ate ele. O perfil MEMBRO so alcanca o cambio e o cofrinho, exatamente
 * como a funcao autorizarAcao_ definia no Apps Script.</p>
 */
@AutoConfigureMockMvc
class PermissoesDaApiTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("sem token nenhuma rota protegida responde")
    void semTokenNaoPassa() throws Exception {
        mockMvc.perform(get("/api/expenses")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/piggy-bank")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/exchange-rate")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("membro nao alcanca gastos, metas, notas, produtos nem categorias")
    void membroNaoAlcancaFinanceiro() throws Exception {
        mockMvc.perform(get("/api/expenses").with(TokensDeTeste.membro()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/monthly-goals").with(TokensDeTeste.membro()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/notes").with(TokensDeTeste.membro()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/products").with(TokensDeTeste.membro()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/categories").with(TokensDeTeste.membro()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("membro deposita e consulta o cofrinho")
    void membroUsaOCofrinho() throws Exception {
        mockMvc.perform(post("/api/piggy-bank/deposits")
                        .with(TokensDeTeste.membro())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-01\",\"valor\":100}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario").value(TokensDeTeste.EMAIL_MEMBRO));

        mockMvc.perform(get("/api/piggy-bank").with(TokensDeTeste.membro()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("titular e admin alcancam o financeiro")
    void perfisPrincipaisAlcancam() throws Exception {
        mockMvc.perform(get("/api/expenses").with(TokensDeTeste.titular())).andExpect(status().isOk());
        mockMvc.perform(get("/api/expenses").with(TokensDeTeste.admin())).andExpect(status().isOk());
        mockMvc.perform(get("/api/categories").with(TokensDeTeste.titular())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("gasto invalido responde 400 com o campo apontado")
    void gastoInvalidoApontaOCampo() throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .with(TokensDeTeste.titular())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-10\",\"itens\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].campo").value("itens"));
    }

    @Test
    @DisplayName("deposito negativo e recusado antes de chegar ao banco")
    void depositoNegativoRecusado() throws Exception {
        mockMvc.perform(post("/api/piggy-bank/deposits")
                        .with(TokensDeTeste.membro())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-01\",\"valor\":-5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("metodo errado responde 405, nao 500")
    void metodoErradoDevolve405() throws Exception {
        mockMvc.perform(post("/api/monthly-goals")
                        .with(TokensDeTeste.titular())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mes\":\"2026-09\",\"limite\":800}"))
                .andExpect(status().isMethodNotAllowed());
    }
}
