package com.piggu.finance.assistente;

import com.piggu.common.dados.Consentimentos;
import com.piggu.common.security.PigguRole;
import com.piggu.testing.DuasFamilias;
import com.piggu.testing.PostgresIntegrationTest;
import com.piggu.testing.TokensDeTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import java.time.YearMonth;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Assistente: Premium, consentimento, limite do mes e as ferramentas lendo so a propria familia. */
@AutoConfigureMockMvc
class AssistenteTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ConversaComGemini gemini;

    private DuasFamilias casas;

    @BeforeEach
    void preparar() {
        casas = new DuasFamilias(mockMvc, json);
        when(gemini.habilitado()).thenReturn(true);
        // O "modelo" usa duas ferramentas e responde com o que elas devolveram.
        when(gemini.responder(anyString(), anyList(), anyString(), anyList(), any())).thenAnswer(chamada -> {
            ConversaComGemini.Executor executor = chamada.getArgument(4);
            Object gastos = executor.executar("gastos_do_mes",
                    json.readTree("{\"mes\":\"2026-09\",\"categoria\":\"Lazer\"}"));
            executor.executar("abrir_tela", json.readTree("{\"tela\":\"orcamentos\"}"));
            return json.writeValueAsString(gastos);
        });
    }

    @Test
    @DisplayName("com o consentimento, responde lendo so os gastos da propria familia e sugere a tela")
    void respondeComAsFerramentas() throws Exception {
        gasto(casas.casaA, "Cinema", 40);
        gasto(casas.casaA, "Mercado", 300, "Alimentação");
        gasto(casas.casaB, "Show", 999);

        perguntar(casas.casaA, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value(org.hamcrest.Matchers.containsString("\"total\":40")))
                .andExpect(jsonPath("$.texto").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Show"))))
                .andExpect(jsonPath("$.tela").value("orcamentos"))
                .andExpect(jsonPath("$.restantes").value(149));

        mockMvc.perform(get("/api/assistant").with(casas.casaA))
                .andExpect(jsonPath("$.autorizado").value(true))
                .andExpect(jsonPath("$.restantes").value(149));
    }

    @Test
    @DisplayName("toda ferramenta devolve dado que vira JSON para a IA, sem e-mail de ninguem")
    void todasAsFerramentas() throws Exception {
        gasto(casas.casaA, "Uber", 25);
        // doAnswer: o when(...) chamaria a resposta do preparar com argumentos nulos.
        doAnswer(chamada -> {
            ConversaComGemini.Executor executor = chamada.getArgument(4);
            StringBuilder tudo = new StringBuilder();
            for (String nome : java.util.List.of("resumo_do_mes", "gastos_do_mes", "buscar_gastos", "orcamentos",
                    "contas_fixas", "cofrinho", "abrir_tela", "inventada")) {
                tudo.append(nome).append('=').append(json.writeValueAsString(executor.executar(nome,
                        json.readTree("{\"mes\":\"2026-09\",\"texto\":\"uber\",\"tela\":\"gastos\"}")))).append('\n');
            }
            return tudo.toString();
        }).when(gemini).responder(anyString(), anyList(), anyString(), anyList(), any());

        perguntar(casas.casaA, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value(org.hamcrest.Matchers.containsString("buscar_gastos={\"texto\":\"uber\",\"total\":25")))
                .andExpect(jsonPath("$.texto").value(org.hamcrest.Matchers.containsString("Ferramenta desconhecida")))
                .andExpect(jsonPath("$.texto").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("@"))))
                .andExpect(jsonPath("$.tela").value("gastos"));
    }

    @Test
    @DisplayName("sem consentimento pede a autorizacao; no gratuito pede o Premium; membro nao entra")
    void portas() throws Exception {
        perguntar(casas.casaA, false)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value(Consentimentos.CODIGO_NECESSARIO));

        UUID familia = UUID.randomUUID();
        RequestPostProcessor gratuita = TokensDeTeste.gratuitaDa(familia, "g-" + familia + "@piggu.test",
                PigguRole.TITULAR);
        perguntar(gratuita, true)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PLANO_PREMIUM"));

        perguntar(TokensDeTeste.membro(), true).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("acabou o limite do mes: recusa sem chamar a IA")
    void limiteDoMes() throws Exception {
        UUID familia = UUID.randomUUID();
        String email = "limite-" + familia + "@piggu.test";
        jdbc.update("INSERT INTO assistant_usage (household_id, user_id, reference_month, questions) VALUES (?, ?, ?, 150)",
                familia, TokensDeTeste.idDe(email), YearMonth.now(java.time.ZoneId.of("America/Sao_Paulo")).toString());

        perguntar(TokensDeTeste.da(familia, email, PigguRole.TITULAR), true)
                .andExpect(status().isTooManyRequests());
    }

    private ResultActions perguntar(RequestPostProcessor quem, boolean autorizo) throws Exception {
        String corpo = autorizo
                ? "{\"pergunta\":\"Quanto gastei com lazer?\",\"moeda\":\"BRL\",\"autorizoIa\":true,\"versaoDoAviso\":\""
                        + Consentimentos.VERSAO_DO_AVISO + "\",\"historico\":[{\"pessoa\":true,\"texto\":\"Oi\"}]}"
                : "{\"pergunta\":\"Quanto gastei com lazer?\"}";
        return mockMvc.perform(post("/api/assistant/ask").with(quem)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private void gasto(RequestPostProcessor quem, String item, int valor) throws Exception {
        gasto(quem, item, valor, "Lazer");
    }

    private void gasto(RequestPostProcessor quem, String item, int valor, String categoria) throws Exception {
        casas.criar("/api/expenses", quem,
                "{\"data\":\"2026-09-10\",\"itens\":[{\"item\":\"%s\",\"categoria\":\"%s\",\"valor\":%d}]}"
                        .formatted(item, categoria, valor));
    }
}
