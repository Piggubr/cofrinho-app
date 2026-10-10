package com.piggu.finance.auditoria;

import com.piggu.common.auditoria.TrilhaDeAuditoria;
import com.piggu.common.security.PigguRole;
import com.piggu.testing.PostgresIntegrationTest;
import com.piggu.testing.TokensDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Quem apagou esse gasto?": cada mudanca no dinheiro da familia deixa autor, hora e o antes e depois. */
@AutoConfigureMockMvc
class TrilhaDeAuditoriaTest extends PostgresIntegrationTest {

    private final UUID familia = UUID.randomUUID();
    private final String emailTitular = "ana-" + familia + "@piggu.test";
    private final String emailParceiro = "beto-" + familia + "@piggu.test";
    private final RequestPostProcessor titular = TokensDeTeste.da(familia, emailTitular, PigguRole.TITULAR);
    private final RequestPostProcessor parceiro = TokensDeTeste.da(familia, emailParceiro, PigguRole.PARCEIRO);
    private final RequestPostProcessor membro = TokensDeTeste.da(familia, "caio-" + familia + "@piggu.test", PigguRole.MEMBRO);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TrilhaDeAuditoria trilha;

    @Test
    @DisplayName("o titular lanca, o parceiro corrige e apaga: o historico mostra os tres, do mais novo ao mais velho")
    void gastoLancadoEditadoEApagado() throws Exception {
        String id = json.readTree(mockMvc.perform(post("/api/expenses").with(titular)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-10\",\"itens\":[{\"item\":\"Pao\",\"categoria\":\"Lazer\",\"valor\":12.5}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get(0).path("id").asString();

        mockMvc.perform(put("/api/expenses/" + id).with(parceiro).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"item\":\"Pao\",\"categoria\":\"Lazer\",\"valor\":15}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/expenses/" + id).with(parceiro)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/history").param("entidade", "gasto").with(titular))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].acao").value("APAGOU"))
                .andExpect(jsonPath("$[0].autor").value(emailParceiro))
                .andExpect(jsonPath("$[0].antes").value("2026-09-10 · Pao · Lazer · 15.00"))
                .andExpect(jsonPath("$[1].acao").value("EDITOU"))
                .andExpect(jsonPath("$[1].antes").value("2026-09-10 · Pao · Lazer · 12.50"))
                .andExpect(jsonPath("$[1].depois").value("2026-09-10 · Pao · Lazer · 15.00"))
                .andExpect(jsonPath("$[2].acao").value("CRIOU"))
                .andExpect(jsonPath("$[2].autor").value(emailTitular))
                .andExpect(jsonPath("$[2].entidadeId").value(id));
    }

    @Test
    @DisplayName("receita, meta e cofrinho tambem entram; o membro nao ve o historico e a vizinha nao ve o nosso")
    void outrasEntidadesEPermissoes() throws Exception {
        mockMvc.perform(post("/api/incomes").with(titular).contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":\"2026-09-05\",\"descricao\":\"Salario\",\"categoria\":\"Salário\",\"valor\":5000}"))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(put("/api/monthly-goals").with(titular).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mes\":\"2026-09\",\"limite\":800}")).andExpect(status().is2xxSuccessful());
        mockMvc.perform(post("/api/piggy-bank/deposits").with(membro).contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":\"2026-09-01\",\"valor\":100}")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/history").with(parceiro))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].entidade").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "receita", "meta", "deposito")));
        mockMvc.perform(get("/api/history").with(membro)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/history").with(TokensDeTeste.da(UUID.randomUUID(), "vizinha@piggu.test", PigguRole.TITULAR)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("mudanca desfeita pela transacao nao deixa evento; editar sem mudar nada tambem nao")
    void soOQueAconteceu() throws Exception {
        mockMvc.perform(post("/api/expenses").with(titular).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-10\",\"contaId\":\"" + UUID.randomUUID()
                                + "\",\"itens\":[{\"item\":\"X\",\"valor\":1}]}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/monthly-goals").with(titular).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mes\":\"2026-10\",\"limite\":500}"));
        mockMvc.perform(put("/api/monthly-goals").with(titular).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mes\":\"2026-10\",\"limite\":500}"));

        mockMvc.perform(get("/api/history").with(titular))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].acao").value("CRIOU"));
    }

    @Test
    @DisplayName("passado o prazo de retencao, o evento sai; pessoa excluida vira conta-excluida no historico")
    void retencaoEExclusao() throws Exception {
        trilha.registrar(familia, emailParceiro, TrilhaDeAuditoria.Acao.CRIOU, "gasto", "velho", null,
                "de muito tempo atras");
        jdbc.update("UPDATE eventos_de_auditoria SET criado_em = ? WHERE entidade_id = 'velho'",
                Timestamp.from(Instant.now().minus(TrilhaDeAuditoria.RETENCAO_PADRAO).minus(Duration.ofDays(1))));
        mockMvc.perform(post("/api/incomes").with(parceiro).contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":\"2026-09-05\",\"descricao\":\"Freela\",\"valor\":300}"));

        assertThat(trilha.apagarAntigos()).isGreaterThanOrEqualTo(1);
        mockMvc.perform(delete("/api/meus-dados")
                        .with(TokensDeTeste.exclusao(familia, emailParceiro, PigguRole.PARCEIRO, "PESSOA")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/history").with(titular))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].autor").value("conta-excluida"));
    }
}
