package com.piggu.finance.domain;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contas fixas: situacao no mes, pagar uma vez so e o lancamento automatico. */
@AutoConfigureMockMvc
class ContasFixasTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private ContasFixasService contas;

    private DuasFamilias casas;

    @BeforeEach
    void preparar() {
        casas = new DuasFamilias(mockMvc, json);
    }

    @Test
    @DisplayName("marcar como paga lanca o gasto no vencimento; o mesmo mes de novo e 409")
    void pagarUmaVez() throws Exception {
        criarConta(casas.casaA, "Aluguel", 31, false);
        String id = idDa(casas.casaA, "2026-02");

        mockMvc.perform(get("/api/bills").param("mes", "2026-02").with(casas.casaA))
                .andExpect(jsonPath("$[0].vencimento").value("2026-02-28"))
                .andExpect(jsonPath("$[0].situacao").value("VENCIDA"));

        mockMvc.perform(post("/api/bills/" + id + "/pay").param("mes", "2026-02").with(casas.casaA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data").value("2026-02-28"))
                .andExpect(jsonPath("$.tipo").value("Fixo"))
                .andExpect(jsonPath("$.origem").value("Conta fixa"));
        mockMvc.perform(post("/api/bills/" + id + "/pay").param("mes", "2026-02").with(casas.casaA))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/bills").param("mes", "2026-02").with(casas.casaA))
                .andExpect(jsonPath("$[0].situacao").value("PAGA"));

        mockMvc.perform(post("/api/bills/" + id + "/pay").param("mes", "2026-02").with(casas.casaB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/bills").with(casas.casaB)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("o job lanca so as automaticas ja vencidas e nao lanca duas vezes")
    void lancamentoAutomatico() throws Exception {
        UUID familia = UUID.randomUUID();
        RequestPostProcessor casa = TokensDeTeste.da(familia, "auto-" + familia + "@piggu.test", PigguRole.TITULAR);
        criarConta(casa, "Internet", 5, true);
        criarConta(casa, "Escola", 20, true);
        criarConta(casa, "Luz", 1, false);

        contas.lancarAutomaticas(LocalDate.of(2026, 10, 9));
        contas.lancarAutomaticas(LocalDate.of(2026, 10, 9));

        mockMvc.perform(get("/api/expenses").param("mes", "2026-10").with(casa))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].item").value("Internet"))
                .andExpect(jsonPath("$[0].usuario").value(TokensDeTeste.idDe("auto-" + familia + "@piggu.test").toString()));
    }

    @Test
    @DisplayName("salario em duas parcelas: a segunda entra no dia dela, uma vez por mes, a partir da proxima vez")
    void salarioEmDuasParcelas() throws Exception {
        UUID familia = UUID.randomUUID();
        RequestPostProcessor casa = TokensDeTeste.da(familia, "salario-" + familia + "@piggu.test", PigguRole.TITULAR);
        String salario = "{\"data\":\"2026-10-20\",\"descricao\":\"Salario\",\"categoria\":\"Salário\",\"valor\":2000,"
                + "\"recorrente\":%s,\"segundoDia\":5%s}";
        mockMvc.perform(post("/api/incomes").with(casa).contentType(MediaType.APPLICATION_JSON)
                .content(salario.formatted("true", ""))).andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/incomes").with(casa).contentType(MediaType.APPLICATION_JSON)
                .content(salario.formatted("false", ",\"segundoValor\":3000"))).andExpect(status().isUnprocessableContent());
        // Adiantamento no dia 20; o resto no dia 5, que ja passou em outubro: so entra em novembro.
        mockMvc.perform(post("/api/incomes").with(casa).contentType(MediaType.APPLICATION_JSON)
                .content(salario.formatted("true", ",\"segundoValor\":3000"))).andExpect(status().isCreated());
        mockMvc.perform(get("/api/bills").param("mes", "2026-10").param("tipo", "RECEITA").with(casa))
                .andExpect(jsonPath("$[0].segundoDia").value(5))
                .andExpect(jsonPath("$[0].segundoValor").value(3000));

        contas.lancarAutomaticas(LocalDate.of(2026, 10, 31));
        contas.lancarAutomaticas(LocalDate.of(2026, 11, 5));
        contas.lancarAutomaticas(LocalDate.of(2026, 11, 5));

        mockMvc.perform(get("/api/incomes").param("mes", "2026-10").with(casa))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/incomes").param("mes", "2026-11").with(casa))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].descricao").value("Salario (2ª parcela)"))
                .andExpect(jsonPath("$[0].data").value("2026-11-05"))
                .andExpect(jsonPath("$[0].valor").value(3000));

        contas.lancarAutomaticas(LocalDate.of(2026, 11, 20));
        mockMvc.perform(get("/api/incomes").param("mes", "2026-11").with(casa))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("o historico do lancamento automatico fica com o dono da conta: o membro nao ve a do titular")
    void historicoDoAutomaticoEDoDono() throws Exception {
        UUID familia = UUID.randomUUID();
        String titular = "auto-titular-" + familia + "@piggu.test";
        RequestPostProcessor doTitular = TokensDeTeste.da(familia, titular, PigguRole.TITULAR);
        RequestPostProcessor membro = TokensDeTeste.da(familia, "auto-membro-" + familia + "@piggu.test", PigguRole.MEMBRO);
        criarConta(doTitular, "Terapia", 5, true);

        contas.lancarAutomaticas(LocalDate.of(2026, 10, 9));

        mockMvc.perform(get("/api/history").with(membro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/history").param("entidade", "gasto").with(doTitular))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].autor").value(TokensDeTeste.idDe(titular).toString()));
    }

    @Test
    @DisplayName("no casal com o mesmo produto na memoria, o job lanca a conta com a categoria dela")
    void automaticoNoCasal() throws Exception {
        UUID familia = UUID.randomUUID();
        String titular = "casal-titular-" + familia + "@piggu.test";
        RequestPostProcessor ela = TokensDeTeste.da(familia, titular, PigguRole.TITULAR);
        RequestPostProcessor ele = TokensDeTeste.da(familia, "casal-parceiro-" + familia + "@piggu.test",
                PigguRole.PARCEIRO);
        for (RequestPostProcessor quem : new RequestPostProcessor[] {ela, ele}) {
            mockMvc.perform(post("/api/expenses").with(quem).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"data\":\"2026-09-10\",\"itens\":[{\"item\":\"Celular\",\"valor\":50}]}"))
                    .andExpect(status().isCreated());
        }
        // A regra dele nao vale para a conta dela.
        mockMvc.perform(put("/api/categories/rules").with(ele).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termo\":\"celular\",\"categoria\":\"Lazer\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bills").with(ela).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\":\"Celular\",\"categoria\":\"Assinaturas\",\"valor\":60,\"dia\":5,\"automatico\":true}"))
                .andExpect(status().isCreated());

        contas.lancarAutomaticas(LocalDate.of(2026, 10, 9));

        mockMvc.perform(get("/api/expenses").param("mes", "2026-10").with(ela))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].categoria").value("Assinaturas"));
        mockMvc.perform(get("/api/products").with(ela))
                .andExpect(jsonPath("$[0].compras").value(2));
    }

    private void criarConta(RequestPostProcessor quem, String descricao, int dia, boolean automatico) throws Exception {
        mockMvc.perform(post("/api/bills").with(quem).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\":\"%s\",\"categoria\":\"Casa\",\"valor\":100,\"dia\":%d,\"automatico\":%s}"
                                .formatted(descricao, dia, automatico)))
                .andExpect(status().isCreated());
    }

    private String idDa(RequestPostProcessor quem, String mes) throws Exception {
        String resposta = mockMvc.perform(get("/api/bills").param("mes", mes).with(quem))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).get(0).path("id").asString();
    }
}
