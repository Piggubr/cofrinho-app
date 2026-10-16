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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cofrinhos por objetivo: deposito por objetivo, quanto guardar por mes e o limite do gratuito. */
@AutoConfigureMockMvc
class CofrinhosPorObjetivoTest extends PostgresIntegrationTest {

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
    @DisplayName("deposito no objetivo soma nele, e o quanto guardar por mes divide o que falta ate o prazo")
    void depositoEPorMes() throws Exception {
        LocalDate prazo = LocalDate.now().plusMonths(3);
        String id = casas.criar("/api/piggy-bank/goals", casas.casaA,
                "{\"nome\":\"Viagem\",\"alvo\":1000,\"prazo\":\"" + prazo + "\"}").path("id").asString();

        casas.criar("/api/piggy-bank/deposits", casas.casaA, "{\"valor\":200,\"objetivo\":\"" + id + "\"}");
        casas.criar("/api/piggy-bank/deposits", casas.casaA, "{\"valor\":50}");

        // Este mes e mais tres: quatro meses para os 800 que faltam.
        mockMvc.perform(get("/api/piggy-bank/goals").with(casas.casaA))
                .andExpect(jsonPath("$[0].guardado").value(200))
                .andExpect(jsonPath("$[0].falta").value(800))
                .andExpect(jsonPath("$[0].percentual").value(20))
                .andExpect(jsonPath("$[0].mesesRestantes").value(4))
                .andExpect(jsonPath("$[0].porMes").value(200))
                .andExpect(jsonPath("$[0].concluido").value(false));
        mockMvc.perform(get("/api/piggy-bank").with(casas.casaA))
                .andExpect(jsonPath("$.depositos[?(@.objetivo=='" + id + "')]").exists());
    }

    @Test
    @DisplayName("outra familia nao ve nem deposita no objetivo; apagar devolve os depositos ao cofrinho geral")
    void isolamentoEExclusao() throws Exception {
        String id = casas.criar("/api/piggy-bank/goals", casas.casaA, "{\"nome\":\"Sofa\",\"alvo\":3000}")
                .path("id").asString();
        casas.criar("/api/piggy-bank/deposits", casas.casaA, "{\"valor\":100,\"objetivo\":\"" + id + "\"}");

        mockMvc.perform(get("/api/piggy-bank/goals").with(casas.casaB)).andExpect(jsonPath("$.length()").value(0));
        deposito(casas.casaB, id).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/piggy-bank/goals/" + id).with(casas.casaB)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"x\",\"alvo\":1}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/piggy-bank/goals/" + id).with(casas.casaA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Sofa novo\",\"alvo\":2500}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/piggy-bank/goals").with(casas.casaA))
                .andExpect(jsonPath("$[0].nome").value("Sofa novo"))
                .andExpect(jsonPath("$[0].porMes").doesNotExist());

        mockMvc.perform(delete("/api/piggy-bank/goals/" + id).with(casas.casaA)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/piggy-bank").with(casas.casaA))
                .andExpect(jsonPath("$.totalDepositos").value(100))
                .andExpect(jsonPath("$.depositos[0].objetivo").doesNotExist());
    }

    @Test
    @DisplayName("gratuito tem um objetivo; o segundo pede o Premium")
    void limiteDoGratuito() throws Exception {
        UUID familia = UUID.randomUUID();
        RequestPostProcessor gratuita = TokensDeTeste.gratuitaDa(familia, "gratis-" + familia + "@piggu.test",
                PigguRole.TITULAR);

        objetivo(gratuita, "Viagem").andExpect(status().isCreated());
        objetivo(gratuita, "Carro")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PLANO_PREMIUM"));

        RequestPostProcessor premium = TokensDeTeste.da(familia, "gratis-" + familia + "@piggu.test",
                PigguRole.TITULAR);
        objetivo(premium, "Carro").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("membro tem os proprios objetivos e nao alcanca o do titular")
    void membro() throws Exception {
        mockMvc.perform(get("/api/piggy-bank/goals").with(TokensDeTeste.membro())).andExpect(status().isOk());
        String id = casas.criar("/api/piggy-bank/goals", TokensDeTeste.titular(), "{\"nome\":\"Bike\",\"alvo\":900}")
                .path("id").asString();
        deposito(TokensDeTeste.membro(), id).andExpect(status().isNotFound());
    }


    private ResultActions objetivo(RequestPostProcessor quem, String nome) throws Exception {
        return mockMvc.perform(post("/api/piggy-bank/goals").with(quem)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"" + nome + "\",\"alvo\":500}"));
    }

    private ResultActions deposito(RequestPostProcessor quem, String objetivo) throws Exception {
        return mockMvc.perform(post("/api/piggy-bank/deposits").with(quem)
                .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":10,\"objetivo\":\"" + objetivo + "\"}"));
    }
}
