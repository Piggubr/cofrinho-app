package com.piggu.finance.api;

import com.piggu.common.web.Meses;
import com.piggu.testing.DuasFamilias;
import com.piggu.testing.PostgresIntegrationTest;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cenarios da auditoria de 11/11/2026 que o finance passou a recusar ou a responder certo. */
@AutoConfigureMockMvc
class RegrasDaAuditoriaTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private DuasFamilias casas;

    @BeforeEach
    void preparar() {
        casas = new DuasFamilias(mockMvc, json);
    }

    private ResultActions enviar(String metodo, String rota, RequestPostProcessor quem, String corpo) throws Exception {
        var pedido = "PUT".equals(metodo) ? put(rota) : post(rota);
        return mockMvc.perform(pedido.with(quem).contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    @Test
    @DisplayName("BUG-007: meta do mes sem mes ou com mes 13 da 400, nao 409 nem grava")
    void mesDaMeta() throws Exception {
        enviar("PUT", "/api/monthly-goals", casas.casaA, "{\"limite\":100}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].campo").value("mes"));
        enviar("PUT", "/api/monthly-goals", casas.casaA, "{\"mes\":\"2026-13\",\"limite\":100}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(Meses.INVALIDO));
        enviar("PUT", "/api/monthly-goals", casas.casaA, "{\"mes\":\"2026-12\",\"limite\":100}")
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("BUG-008: gasto, edicao e nota de R$ 0,00 sao recusados; o teto tem texto acentuado")
    void valorMinimoETeto() throws Exception {
        enviar("POST", "/api/expenses", casas.casaA, "{\"data\":\"2026-09-10\",\"itens\":[{\"item\":\"Nada\",\"valor\":0}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Digite um valor a partir de R$ 0,01."));
        enviar("POST", "/api/expenses", casas.casaA,
                "{\"data\":\"2026-09-10\",\"itens\":[{\"item\":\"Carro\",\"valor\":1000000.01}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("O valor máximo é R$ 1.000.000,00."));
        enviar("POST", "/api/incomes", casas.casaA,
                "{\"data\":\"2026-09-10\",\"descricao\":\"Bolada\",\"valor\":10000000.01}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("O valor máximo é R$ 10.000.000,00."));
        enviar("POST", "/api/notes", casas.casaA, "{\"titulo\":\"Festa\",\"data\":\"2026-09-10\",\"valor\":0}")
                .andExpect(status().isBadRequest());

        String id = casas.criar("/api/expenses", casas.casaA,
                "{\"data\":\"2026-09-10\",\"itens\":[{\"item\":\"Pao\",\"valor\":10}]}").get(0).path("id").asString();
        enviar("PUT", "/api/expenses/" + id, casas.casaA, "{\"item\":\"Pao\",\"valor\":0}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("BUG-023: data fora de 2000 ate hoje + 2 anos e recusada no campo data, com o ano no texto")
    void faixaDeDatas() throws Exception {
        int teto = LocalDate.now(Meses.BRASILIA).plusYears(2).getYear();
        String texto = "Confira a data: use uma data entre 2000 e " + teto + ".";
        enviar("POST", "/api/expenses", casas.casaA, "{\"data\":\"1900-01-01\",\"itens\":[{\"item\":\"Pao\",\"valor\":10}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].campo").value("data"))
                .andExpect(jsonPath("$.erro").value(texto));
        enviar("POST", "/api/incomes", casas.casaA,
                "{\"data\":\"" + (teto + 1) + "-01-01\",\"descricao\":\"Salario\",\"valor\":10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("data"));
        enviar("POST", "/api/piggy-bank/deposits", casas.casaA, "{\"data\":\"1999-12-31\",\"valor\":10}")
                .andExpect(status().isBadRequest());
        enviar("POST", "/api/incomes/receivables", casas.casaA,
                "{\"quem\":\"Ana\",\"valor\":10,\"data\":\"2099-12-31\"}")
                .andExpect(status().isBadRequest());

        String id = casas.criar("/api/expenses", casas.casaA,
                "{\"data\":\"2000-01-01\",\"itens\":[{\"item\":\"Pao\",\"valor\":10}]}").get(0).path("id").asString();
        enviar("PUT", "/api/expenses/" + id, casas.casaA, "{\"item\":\"Pao\",\"valor\":10,\"data\":\"1900-01-01\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("BUG-022: dividirCom com id de outra familia ou inventado da 400 e nao grava; com quem e da casa, divide")
    void dividirSoComAFamilia() throws Exception {
        UUID estranho = UUID.randomUUID();
        enviar("POST", "/api/expenses", casas.casaA, "{\"data\":\"2026-07-10\",\"dividirCom\":[\"" + estranho
                + "\"],\"itens\":[{\"item\":\"Jantar\",\"valor\":30}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Só dá para dividir com pessoas da sua família."));
        mockMvc.perform(get("/api/expenses").param("mes", "2026-07").with(casas.casaA))
                .andExpect(jsonPath("$.length()").value(0));

        UUID ana = casas.pessoaDaA("ana-" + UUID.randomUUID() + "@piggu.test");
        casas.criar("/api/expenses", casas.casaA, "{\"data\":\"2026-07-10\",\"dividirCom\":[\"" + ana
                + "\"],\"itens\":[{\"item\":\"Jantar\",\"valor\":30}]}");
        mockMvc.perform(get("/api/expenses/splits").param("mes", "2026-07").with(casas.casaA))
                .andExpect(jsonPath("$[?(@.pessoa=='" + ana + "')].saldo").value(-30.0));
    }

    @Test
    @DisplayName("receita com o mesmo idDoEnvio duas vezes grava uma vez e responde igual")
    void receitaSemDuplicar() throws Exception {
        String corpo = "{\"data\":\"2026-09-05\",\"descricao\":\"Salario\",\"valor\":3000,\"idDoEnvio\":\""
                + UUID.randomUUID() + "\"}";
        String primeira = enviar("POST", "/api/incomes", casas.casaA, corpo)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        enviar("POST", "/api/incomes", casas.casaA, corpo)
                .andExpect(status().isCreated())
                .andExpect(content().json(primeira, true));
        enviar("POST", "/api/incomes", casas.casaB, corpo).andExpect(status().isCreated());

        mockMvc.perform(get("/api/incomes").param("mes", "2026-09").with(casas.casaA))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("importacao: linha de valor zero sai da previa sem derrubar o arquivo")
    void linhaZeroForaDaPrevia() throws Exception {
        String csv = "Data;Descricao;Valor\\n10/09/2026;Mercado;-50,00\\n11/09/2026;Estorno;0,00\\n";
        enviar("POST", "/api/expenses/import/preview", casas.casaA, "{\"conteudo\":\"" + csv + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].descricao").value("Mercado"));
        enviar("POST", "/api/expenses/import/preview-lines", casas.casaA, "{\"linhas\":["
                + "{\"data\":\"2026-09-10\",\"descricao\":\"Tarifa\",\"valor\":0,\"idExterno\":\"x1\"},"
                + "{\"data\":\"2026-09-10\",\"descricao\":\"Padaria\",\"valor\":12,\"idExterno\":\"x2\"}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("projecao do mes: campos novos presentes; fora do mes corrente, nulos (D-190)")
    void camposDaProjecao() throws Exception {
        casas.criar("/api/expenses", casas.casaA,
                "{\"data\":\"2026-08-05\",\"itens\":[{\"item\":\"Aluguel\",\"valor\":2000,\"tipo\":\"Fixo\"},"
                        + "{\"item\":\"Mercado\",\"valor\":300}]}");
        mockMvc.perform(get("/api/reports/month").param("mes", "2026-08").with(casas.casaA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gastosFixos").value(2000.0))
                .andExpect(jsonPath("$.projecaoDeGastos").doesNotExist())
                .andExpect(jsonPath("$.gastosAteMesmoDiaAnterior").doesNotExist())
                .andExpect(jsonPath("$.variacaoAteMesmoDia").doesNotExist());
    }

    @Test
    @DisplayName("importacao: linha zero escolhida nao grava e conta como pulada")
    void linhaZeroNaoGrava() throws Exception {
        enviar("POST", "/api/expenses/import", casas.casaA, "{\"linhas\":["
                + "{\"data\":\"2026-06-10\",\"descricao\":\"Tarifa\",\"valor\":0,\"idExterno\":\"z1\"},"
                + "{\"data\":\"2026-06-10\",\"descricao\":\"Padaria\",\"valor\":12,\"idExterno\":\"z2\"}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importados").value(1))
                .andExpect(jsonPath("$.pulados").value(1));
        mockMvc.perform(get("/api/expenses").param("mes", "2026-06").with(casas.casaA))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].item").value("Padaria"));
    }

    @Test
    @DisplayName("projecao no mes corrente: o fixo conta uma vez, o variavel vai pelo ritmo e compara ate o mesmo dia (D-190)")
    void projecaoNoMesCorrente() throws Exception {
        LocalDate hoje = LocalDate.now(Meses.BRASILIA);
        YearMonth mes = YearMonth.from(hoje);
        YearMonth anterior = mes.minusMonths(1);
        casas.criar("/api/expenses", casas.casaA, "{\"data\":\"" + mes.atDay(1)
                + "\",\"itens\":[{\"item\":\"Aluguel\",\"valor\":2000,\"tipo\":\"Fixo\"}]}");
        casas.criar("/api/expenses", casas.casaA, "{\"data\":\"" + hoje
                + "\",\"itens\":[{\"item\":\"Mercado\",\"valor\":300}]}");
        // Do mes anterior, no dia 1: sempre entra na comparacao ate o mesmo dia de hoje.
        casas.criar("/api/expenses", casas.casaA, "{\"data\":\"" + anterior.atDay(1)
                + "\",\"itens\":[{\"item\":\"Feira\",\"valor\":100}]}");

        BigDecimal projecao = new BigDecimal("300").multiply(BigDecimal.valueOf(mes.lengthOfMonth()))
                .divide(BigDecimal.valueOf(hoje.getDayOfMonth()), 2, RoundingMode.HALF_UP).add(new BigDecimal("2000"));
        mockMvc.perform(get("/api/reports/month").with(casas.casaA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gastosFixos").value(2000.0))
                .andExpect(jsonPath("$.projecaoDeGastos").value(projecao.doubleValue()))
                .andExpect(jsonPath("$.gastosAteMesmoDiaAnterior").value(100.0))
                .andExpect(jsonPath("$.variacaoAteMesmoDia").value(2200.0));
    }
}
