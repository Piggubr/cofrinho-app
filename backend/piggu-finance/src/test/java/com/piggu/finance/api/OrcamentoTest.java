package com.piggu.finance.api;

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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Orcamento por categoria: alerta em 80% e 100%, e o que e Premium. */
@AutoConfigureMockMvc
class OrcamentoTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private DuasFamilias casas;

    @BeforeEach
    void preparar() {
        casas = new DuasFamilias(mockMvc, json);
    }

    @Test
    @DisplayName("85% do limite e atencao, 100% estourou, abaixo de 80% ok; outra familia nao ve")
    void alertas() throws Exception {
        orcamento(casas.casaA, "Alimentação", 100).andExpect(status().isNoContent());
        orcamento(casas.casaA, "Transporte", 50).andExpect(status().isNoContent());
        orcamento(casas.casaA, "Lazer", 1000).andExpect(status().isNoContent());
        gasto("Alimentação", 85);
        gasto("Transporte", 50);
        gasto("Lazer", 10);

        mockMvc.perform(get("/api/budgets").param("mes", "2026-09").with(casas.casaA))
                .andExpect(jsonPath("$[?(@.categoria=='Alimentação')].alerta").value("ATENCAO"))
                .andExpect(jsonPath("$[?(@.categoria=='Alimentação')].percentual").value(85))
                .andExpect(jsonPath("$[?(@.categoria=='Transporte')].alerta").value("ESTOUROU"))
                .andExpect(jsonPath("$[?(@.categoria=='Lazer')].alerta").value("OK"));
        mockMvc.perform(get("/api/budgets").with(casas.casaB)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("criar orcamento e Premium; ver e apagar o que ja existe, nao")
    void premium() throws Exception {
        UUID familia = UUID.randomUUID();
        String email = "gratis-" + familia + "@piggu.test";
        RequestPostProcessor gratuita = TokensDeTeste.como(email, PigguRole.TITULAR, UUID.nameUUIDFromBytes(email.getBytes()),
                "GRATUITO");

        orcamento(gratuita, "Lazer", 100)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PLANO_PREMIUM"));

        orcamento(casas.casaA, "Lazer", 100).andExpect(status().isNoContent());
        String id = json.readTree(mockMvc.perform(get("/api/budgets").with(casas.casaA))
                .andReturn().getResponse().getContentAsString()).get(0).path("id").asString();
        mockMvc.perform(delete("/api/budgets/" + id).with(casas.casaB)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/budgets/" + id).with(casas.casaA)).andExpect(status().isNoContent());
    }

    private org.springframework.test.web.servlet.ResultActions orcamento(RequestPostProcessor quem, String categoria,
                                                                        int limite) throws Exception {
        return mockMvc.perform(put("/api/budgets").with(quem).contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoria\":\"%s\",\"limite\":%d}".formatted(categoria, limite)));
    }

    private void gasto(String categoria, int valor) throws Exception {
        casas.criar("/api/expenses", casas.casaA,
                "{\"data\":\"2026-09-10\",\"itens\":[{\"item\":\"x\",\"categoria\":\"%s\",\"valor\":%d}]}"
                        .formatted(categoria, valor));
    }
}
