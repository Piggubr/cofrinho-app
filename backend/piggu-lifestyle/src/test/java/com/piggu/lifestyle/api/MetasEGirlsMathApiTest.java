package com.piggu.lifestyle.api;

import com.piggu.common.security.PigguRole;
import com.piggu.common.web.Meses;
import com.piggu.testing.PostgresIntegrationTest;
import com.piggu.testing.TokensDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Metas do mes e Girls Math pela API: todo papel usa, e cada pessoa so enxerga o que e dela,
 * mesmo dentro da mesma familia (nem a titular ve o da parceira).
 */
@AutoConfigureMockMvc
class MetasEGirlsMathApiTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    /** Pessoa nova a cada chamada, na familia padrao dos testes: nada de outro teste aparece. */
    private static RequestPostProcessor pessoa(PigguRole papel) {
        return TokensDeTeste.como("pessoa-" + UUID.randomUUID() + "@piggu.test", papel);
    }

    @Test
    @DisplayName("todos os papeis, inclusive membro, criam e listam as proprias metas e itens")
    void todosOsPapeis() throws Exception {
        for (PigguRole papel : PigguRole.values()) {
            RequestPostProcessor quem = pessoa(papel);
            enviar(post("/api/personal-goals"), quem, "{\"titulo\":\"Meta\",\"alvo\":2}").andExpect(status().isCreated());
            enviar(post("/api/girls-math"), quem, "{\"produto\":\"Bolsa\",\"valor\":10}").andExpect(status().isCreated());
            mockMvc.perform(get("/api/personal-goals").with(quem)).andExpect(jsonPath("$.length()").value(1));
            mockMvc.perform(get("/api/girls-math").with(quem)).andExpect(jsonPath("$.length()").value(1));
        }
    }

    @Test
    @DisplayName("meta: campos da resposta, concluida ao chegar no alvo, mes padrao e mes informado")
    void metaPelaApi() throws Exception {
        RequestPostProcessor ana = pessoa(PigguRole.MEMBRO);
        String mes = YearMonth.now(Meses.BRASILIA).toString();

        String id = json.readTree(enviar(post("/api/personal-goals"), ana, "{\"titulo\":\"Correr\",\"alvo\":3}")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.titulo").value("Correr"))
                        .andExpect(jsonPath("$.alvo").value(3))
                        .andExpect(jsonPath("$.atual").value(0))
                        .andExpect(jsonPath("$.mes").value(mes))
                        .andExpect(jsonPath("$.concluida").value(false))
                        .andReturn().getResponse().getContentAsString())
                .path("id").asString();

        enviar(put("/api/personal-goals/" + id), ana, "{\"titulo\":\"Correr\",\"alvo\":3,\"atual\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concluida").value(true));

        mockMvc.perform(get("/api/personal-goals").param("mes", mes).with(ana))
                .andExpect(jsonPath("$[0].id").value(id));
        mockMvc.perform(get("/api/personal-goals").param("mes", "2020-01").with(ana))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/personal-goals").param("mes", "janeiro").with(ana))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/personal-goals/" + id).with(ana)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/personal-goals").with(ana)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("girls math: campos da resposta e data padrao de hoje")
    void girlsMathPelaApi() throws Exception {
        RequestPostProcessor ana = pessoa(PigguRole.PARCEIRO);

        String id = json.readTree(enviar(post("/api/girls-math"), ana,
                        "{\"produto\":\"Tenis\",\"loja\":\"Shopping\",\"valor\":499.9}")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.produto").value("Tenis"))
                        .andExpect(jsonPath("$.loja").value("Shopping"))
                        .andExpect(jsonPath("$.valor").value(499.9))
                        .andExpect(jsonPath("$.data").value(LocalDate.now(Meses.BRASILIA).toString()))
                        .andExpect(jsonPath("$.temFoto").value(false))
                        .andReturn().getResponse().getContentAsString())
                .path("id").asString();

        enviar(post("/api/girls-math"), ana, "{\"produto\":\"Casaco\",\"valor\":200,\"data\":\"2025-01-10\"}")
                .andExpect(jsonPath("$.data").value("2025-01-10"));
        mockMvc.perform(get("/api/girls-math").with(ana))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[1].produto").value("Casaco"));

        mockMvc.perform(delete("/api/girls-math/" + id).with(ana)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/girls-math").with(ana)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("na exportacao de dados a titular leva so as proprias metas e itens, nao os da parceira")
    void exportacaoSoDoProprio() throws Exception {
        RequestPostProcessor parceira = pessoa(PigguRole.PARCEIRO);
        RequestPostProcessor titular = pessoa(PigguRole.TITULAR);
        enviar(post("/api/personal-goals"), parceira, "{\"titulo\":\"Segredo\",\"alvo\":1}").andExpect(status().isCreated());
        enviar(post("/api/girls-math"), parceira, "{\"produto\":\"Segredo\",\"valor\":10}").andExpect(status().isCreated());
        enviar(post("/api/personal-goals"), titular, "{\"titulo\":\"Minha\",\"alvo\":1}").andExpect(status().isCreated());

        mockMvc.perform(get("/api/meus-dados").with(titular))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personal_goals.length()").value(1))
                .andExpect(jsonPath("$.personal_goals[0].title").value("Minha"))
                .andExpect(jsonPath("$.girls_math_items.length()").value(0));
        mockMvc.perform(get("/api/meus-dados").with(parceira))
                .andExpect(jsonPath("$.personal_goals[0].title").value("Segredo"))
                .andExpect(jsonPath("$.girls_math_items.length()").value(1));
    }

    @Test
    @DisplayName("nem a titular da mesma familia ve, edita ou apaga a meta ou o item de outra pessoa")
    void soADona() throws Exception {
        RequestPostProcessor parceira = pessoa(PigguRole.PARCEIRO);
        RequestPostProcessor titular = pessoa(PigguRole.TITULAR);
        String meta = "{\"titulo\":\"Ler\",\"alvo\":3}";
        String idMeta = json.readTree(enviar(post("/api/personal-goals"), parceira, meta)
                .andReturn().getResponse().getContentAsString()).path("id").asString();
        String idItem = json.readTree(enviar(post("/api/girls-math"), parceira, "{\"produto\":\"Bolsa\",\"valor\":50}")
                .andReturn().getResponse().getContentAsString()).path("id").asString();

        mockMvc.perform(get("/api/personal-goals").with(titular)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/girls-math").with(titular)).andExpect(jsonPath("$.length()").value(0));
        enviar(put("/api/personal-goals/" + idMeta), titular, meta).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/personal-goals/" + idMeta).with(titular)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/girls-math/" + idItem).with(titular)).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/personal-goals").with(parceira)).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/girls-math").with(parceira)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("fora do formato da 400 com o campo")
    void validacao() throws Exception {
        RequestPostProcessor ana = pessoa(PigguRole.TITULAR);

        enviar(post("/api/personal-goals"), ana, "{\"titulo\":\"\",\"alvo\":3}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos[0].campo").value("titulo"));
        enviar(post("/api/personal-goals"), ana, "{\"titulo\":\"" + "a".repeat(81) + "\",\"alvo\":3}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos[0].campo").value("titulo"));
        enviar(post("/api/personal-goals"), ana, "{\"titulo\":\"Ler\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos[0].campo").value("alvo"));
        enviar(post("/api/personal-goals"), ana, "{\"titulo\":\"Ler\",\"alvo\":0}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos[0].campo").value("alvo"));
        enviar(post("/api/personal-goals"), ana, "{\"titulo\":\"Ler\",\"alvo\":1000001}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos[0].campo").value("alvo"));
        enviar(post("/api/personal-goals"), ana, "{\"titulo\":\"Ler\",\"alvo\":3,\"atual\":-1}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos[0].campo").value("atual"));

        enviar(post("/api/girls-math"), ana, "{\"produto\":\" \",\"valor\":10}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos[0].campo").value("produto"));
        enviar(post("/api/girls-math"), ana, "{\"produto\":\"Bolsa\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos[0].campo").value("valor"));
        enviar(post("/api/girls-math"), ana, "{\"produto\":\"Bolsa\",\"valor\":0}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos[0].campo").value("valor"));
        enviar(post("/api/girls-math"), ana, "{\"produto\":\"Bolsa\",\"valor\":1000000.01}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos[0].campo").value("valor"));
        enviar(post("/api/girls-math"), ana, "{\"produto\":\"Bolsa\",\"loja\":\"" + "a".repeat(121) + "\",\"valor\":10}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos[0].campo").value("loja"));
        enviar(post("/api/girls-math"), ana, "{\"produto\":\"Bolsa\",\"valor\":10,\"mimeType\":\"text/html\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos[0].campo").value("mimeType"));

        mockMvc.perform(get("/api/personal-goals").with(ana)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/girls-math").with(ana)).andExpect(jsonPath("$.length()").value(0));
    }

    private ResultActions enviar(MockHttpServletRequestBuilder pedido, RequestPostProcessor quem, String corpo)
            throws Exception {
        return mockMvc.perform(pedido.with(quem).contentType(MediaType.APPLICATION_JSON).content(corpo));
    }
}
