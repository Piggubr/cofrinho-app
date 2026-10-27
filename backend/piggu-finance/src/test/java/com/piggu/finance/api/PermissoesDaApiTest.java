package com.piggu.finance.api;

import com.piggu.testing.PostgresIntegrationTest;
import com.piggu.testing.TokensDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
 * chegar ate ele. Todo perfil da familia alcanca o financeiro; o que cada um ve e
 * filtrado por dono (ver CadaUmVeOSeuTest).</p>
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
    @DisplayName("membro alcanca gastos, metas, notas, produtos e categorias (so os dele)")
    void membroAlcancaFinanceiro() throws Exception {
        mockMvc.perform(get("/api/expenses").with(TokensDeTeste.membro()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/monthly-goals").with(TokensDeTeste.membro()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notes").with(TokensDeTeste.membro()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/products").with(TokensDeTeste.membro()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/categories").with(TokensDeTeste.membro()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/alerts").with(TokensDeTeste.membro()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("avisos de hoje: familia sem nada cadastrado nao tem aviso")
    void avisosDeHoje() throws Exception {
        mockMvc.perform(get("/api/alerts").with(TokensDeTeste.parceiro()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$").isArray());
    }

    @Test
    @DisplayName("membro deposita e consulta o cofrinho")
    void membroUsaOCofrinho() throws Exception {
        mockMvc.perform(post("/api/piggy-bank/deposits")
                        .with(TokensDeTeste.membro())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-01\",\"valor\":100}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario").value(TokensDeTeste.idDe(TokensDeTeste.EMAIL_MEMBRO).toString()));

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
    @DisplayName("conta sem CPF nao alcanca os dados ate informar (403 CPF_PENDENTE)")
    void semCpfNaoAlcanca() throws Exception {
        mockMvc.perform(get("/api/expenses").with(TokensDeTeste.titularSemCpf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CPF_PENDENTE"));
    }

    @Test
    @DisplayName("cadastro pela metade (entrou pelo Google e nao completou) nao alcanca os dados (403 CADASTRO_PENDENTE)")
    void cadastroIncompletoNaoAlcanca() throws Exception {
        mockMvc.perform(get("/api/expenses").with(org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> token
                                .subject(TokensDeTeste.idDe(TokensDeTeste.EMAIL_TITULAR).toString())
                                .claim("role", "TITULAR").claim("familia", TokensDeTeste.FAMILIA.toString())
                                .claim("cadastro", false))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CADASTRO_PENDENTE"));
    }

    @Test
    @DisplayName("parceiro lanca e consulta gastos, receitas, orcamentos e contas como o titular")
    void parceiroLanca() throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .with(TokensDeTeste.parceiro())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-10\",\"itens\":[{\"item\":\"Pao\",\"valor\":12.5}]}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/expenses").with(TokensDeTeste.parceiro())).andExpect(status().isOk());
        mockMvc.perform(get("/api/incomes").with(TokensDeTeste.parceiro())).andExpect(status().isOk());
        mockMvc.perform(get("/api/budgets").with(TokensDeTeste.parceiro())).andExpect(status().isOk());
        mockMvc.perform(get("/api/bills").with(TokensDeTeste.parceiro())).andExpect(status().isOk());
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
