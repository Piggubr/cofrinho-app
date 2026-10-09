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
                .andExpect(jsonPath("$[0].usuario").value("auto-" + familia + "@piggu.test"));
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
