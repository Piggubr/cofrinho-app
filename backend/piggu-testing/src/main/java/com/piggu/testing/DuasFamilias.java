package com.piggu.testing;

import com.piggu.common.security.PigguRole;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Duas familias novas, cada uma com a titular Premium, para os testes de isolamento.
 *
 * <p>Familias novas a cada teste: o que outras classes gravaram no mesmo banco nao
 * interfere, e uma lista vazia prova que nada vazou.</p>
 */
public final class DuasFamilias {

    public final RequestPostProcessor casaA = titularDe(UUID.randomUUID());
    public final RequestPostProcessor casaB = titularDe(UUID.randomUUID());

    private final MockMvc mockMvc;
    private final ObjectMapper json;

    public DuasFamilias(MockMvc mockMvc, ObjectMapper json) {
        this.mockMvc = mockMvc;
        this.json = json;
    }

    private static RequestPostProcessor titularDe(UUID familia) {
        return TokensDeTeste.da(familia, "titular-" + familia + "@piggu.test", PigguRole.TITULAR);
    }

    /** POST que precisa dar certo; devolve o corpo da resposta. */
    public JsonNode criar(String rota, RequestPostProcessor quem, String corpo) throws Exception {
        String resposta = mockMvc.perform(post(rota).with(quem)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return resposta.isEmpty() ? json.nullNode() : json.readTree(resposta);
    }
}
