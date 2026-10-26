package com.piggu.finance.api;

import com.piggu.common.security.PigguRole;
import com.piggu.testing.PostgresIntegrationTest;
import com.piggu.testing.TokensDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Plano casal: cada um ve o seu e o que o outro compartilhou. O compartilhado os dois
 * editam, mas so quem lancou apaga ou volta para "so meu".
 */
@AutoConfigureMockMvc
class CompartilhadoTest extends PostgresIntegrationTest {

    private static final String ELA = "ela.casal@piggu.test";
    private static final String ELE = "ele.casal@piggu.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Test
    @DisplayName("o parceiro ve so o compartilhado; edita, mas nao apaga nem descompartilha")
    void gastoCompartilhado() throws Exception {
        UUID casa = UUID.randomUUID();
        RequestPostProcessor ela = TokensDeTeste.da(casa, ELA, PigguRole.TITULAR);
        RequestPostProcessor ele = TokensDeTeste.da(casa, ELE, PigguRole.PARCEIRO);
        lancar(ela, "Presente surpresa", null, null);
        String aluguel = lancar(ela, "Aluguel", true, null);

        mockMvc.perform(get("/api/expenses").with(ele))
                .andExpect(jsonPath("$[*].item").value(containsInAnyOrder("Aluguel")))
                .andExpect(jsonPath("$[0].compartilhado").value(true));

        mockMvc.perform(put("/api/expenses/" + aluguel).with(ele).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"item\":\"Aluguel de outubro\",\"categoria\":\"Casa\",\"valor\":1500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item").value("Aluguel de outubro"));
        mockMvc.perform(put("/api/expenses/" + aluguel).with(ele).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"item\":\"Aluguel\",\"categoria\":\"Casa\",\"valor\":1500,\"compartilhado\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/expenses/" + aluguel).with(ele)).andExpect(status().isForbidden());

        mockMvc.perform(put("/api/expenses/" + aluguel).with(ela).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"item\":\"Aluguel\",\"categoria\":\"Casa\",\"valor\":1500,\"compartilhado\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/expenses").with(ele)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("apagar as proximas parcelas com o recibo do outro nao leva as parcelas dele")
    void parcelasDoOutroFicam() throws Exception {
        UUID casa = UUID.randomUUID();
        RequestPostProcessor ela = TokensDeTeste.da(casa, ELA, PigguRole.TITULAR);
        RequestPostProcessor ele = TokensDeTeste.da(casa, ELE, PigguRole.PARCEIRO);
        String tvDela = mockMvc.perform(post("/api/expenses").with(ela).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-10\",\"parcelas\":3,\"compartilhado\":true,"
                                + "\"itens\":[{\"item\":\"TV\",\"valor\":900}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String recibo = json.readTree(tvDela).path(0).path("reciboId").asString();

        // Ele copia o recibo, o item e as parcelas que ve na lista e apaga a propria parcela 1 "com as proximas".
        String copia = mockMvc.perform(post("/api/expenses").with(ele).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-10\",\"parcelas\":3,\"reciboId\":\"" + recibo + "\","
                                + "\"itens\":[{\"item\":\"TV\",\"valor\":3}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(delete("/api/expenses/" + json.readTree(copia).path(0).path("id").asString())
                .param("proximas", "true").with(ele)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/expenses").with(ela)).andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("o cartao do casal compartilha o que passa nele; so quem criou apaga o cartao")
    void cartaoDoCasal() throws Exception {
        UUID casa = UUID.randomUUID();
        RequestPostProcessor ela = TokensDeTeste.da(casa, ELA, PigguRole.TITULAR);
        RequestPostProcessor ele = TokensDeTeste.da(casa, ELE, PigguRole.PARCEIRO);
        String cartao = json.readTree(mockMvc.perform(post("/api/accounts").with(ela)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Cartao da casa\",\"tipo\":\"CARTAO\",\"vencimento\":10,\"compartilhado\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.compartilhado").value(true))
                .andReturn().getResponse().getContentAsString()).path("id").asString();

        // Ele ve o cartao e lanca nele; o gasto ja nasce compartilhado.
        lancar(ele, "Mercado", null, cartao);
        mockMvc.perform(get("/api/expenses").with(ela))
                .andExpect(jsonPath("$[*].item").value(containsInAnyOrder("Mercado")));
        // Escolher "so meu" vale mais que o cartao.
        lancar(ele, "Cerveja com os amigos", false, cartao);
        mockMvc.perform(get("/api/expenses").with(ela))
                .andExpect(jsonPath("$[*].item").value(containsInAnyOrder("Mercado")));

        mockMvc.perform(delete("/api/accounts/" + cartao).with(ele)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/accounts/" + cartao + "/sharing").with(ele).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"compartilhado\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/accounts/" + cartao).with(ela)).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("painel: visao meu soma o que eu lancei; visao casal, so o compartilhado")
    void visaoDoPainel() throws Exception {
        UUID casa = UUID.randomUUID();
        RequestPostProcessor ela = TokensDeTeste.da(casa, ELA, PigguRole.TITULAR);
        RequestPostProcessor ele = TokensDeTeste.da(casa, ELE, PigguRole.PARCEIRO);
        lancar(ela, "Salao", null, null);
        lancar(ela, "Luz", true, null);
        lancar(ele, "Internet", true, null);
        mockMvc.perform(post("/api/incomes").with(ela).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-05\",\"descricao\":\"Salario\",\"valor\":1000}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/incomes").with(ele).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-05\",\"descricao\":\"Vale da casa\",\"valor\":300,\"compartilhado\":true}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/expenses").param("mes", "2026-09").with(ela))
                .andExpect(jsonPath("$[*].item").value(containsInAnyOrder("Salao", "Luz", "Internet")));
        mockMvc.perform(get("/api/expenses").param("mes", "2026-09").param("visao", "meu").with(ela))
                .andExpect(jsonPath("$[*].item").value(containsInAnyOrder("Salao", "Luz")));
        mockMvc.perform(get("/api/expenses").param("mes", "2026-09").param("visao", "casal").with(ela))
                .andExpect(jsonPath("$[*].item").value(containsInAnyOrder("Luz", "Internet")));

        mockMvc.perform(get("/api/reports/month").param("mes", "2026-09").param("visao", "casal").with(ela))
                .andExpect(jsonPath("$.gastos").value(20))
                .andExpect(jsonPath("$.receitas").value(300));
        mockMvc.perform(get("/api/reports/month").param("mes", "2026-09").with(ele))
                .andExpect(jsonPath("$.gastos").value(20))
                .andExpect(jsonPath("$.receitas").value(300));
    }

    @Test
    @DisplayName("meta a dois: os dois depositam no cofrinho compartilhado e veem o total")
    void metaADois() throws Exception {
        UUID casa = UUID.randomUUID();
        RequestPostProcessor ela = TokensDeTeste.da(casa, ELA, PigguRole.TITULAR);
        RequestPostProcessor ele = TokensDeTeste.da(casa, ELE, PigguRole.PARCEIRO);
        String viagem = json.readTree(mockMvc.perform(post("/api/piggy-bank/goals").with(ela)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Viagem\",\"alvo\":1000,\"compartilhado\":true}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("id").asString();

        depositar(ela, 100, viagem);
        depositar(ele, 150, viagem);

        mockMvc.perform(get("/api/piggy-bank/goals").with(ela))
                .andExpect(jsonPath("$[0].guardado").value(250))
                .andExpect(jsonPath("$[0].compartilhado").value(true));
        mockMvc.perform(get("/api/piggy-bank/goals").with(ele))
                .andExpect(jsonPath("$[0].guardado").value(250));
        mockMvc.perform(delete("/api/piggy-bank/goals/" + viagem).with(ele)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("acerto do mes: quem deve quanto a quem")
    void quemDeveAQuem() throws Exception {
        UUID casa = UUID.randomUUID();
        RequestPostProcessor ela = TokensDeTeste.da(casa, ELA, PigguRole.TITULAR);
        String dividir = "[\"" + TokensDeTeste.idDe(ELA) + "\",\"" + TokensDeTeste.idDe(ELE) + "\"]";
        mockMvc.perform(post("/api/expenses").with(ela).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-10\",\"compartilhado\":true,\"dividirCom\":" + dividir
                                + ",\"itens\":[{\"item\":\"Mercado\",\"valor\":300}]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/expenses/splits/transfers").param("mes", "2026-09").with(ela))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].de").value(TokensDeTeste.idDe(ELE).toString()))
                .andExpect(jsonPath("$[0].para").value(TokensDeTeste.idDe(ELA).toString()))
                .andExpect(jsonPath("$[0].valor").value(150));
    }

    private void depositar(RequestPostProcessor quem, int valor, String objetivo) throws Exception {
        mockMvc.perform(post("/api/piggy-bank/deposits").with(quem).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-01\",\"valor\":" + valor + ",\"objetivo\":\"" + objetivo + "\"}"))
                .andExpect(status().isCreated());
    }

    private String lancar(RequestPostProcessor quem, String item, Boolean compartilhado, String conta) throws Exception {
        String extra = (compartilhado == null ? "" : ",\"compartilhado\":" + compartilhado)
                + (conta == null ? "" : ",\"contaId\":\"" + conta + "\"");
        String resposta = mockMvc.perform(post("/api/expenses").with(quem).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-10\"" + extra + ",\"itens\":[{\"item\":\"" + item
                                + "\",\"valor\":10}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).get(0).path("id").asString();
    }
}
