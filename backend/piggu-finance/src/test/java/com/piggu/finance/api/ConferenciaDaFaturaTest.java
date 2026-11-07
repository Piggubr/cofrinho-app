package com.piggu.finance.api;

import com.piggu.common.dados.Consentimentos;
import com.piggu.common.error.BusinessException;
import com.piggu.common.security.PigguRole;
import com.piggu.finance.integration.GeminiFaturaReader;
import com.piggu.finance.integration.LeitorDeFatura.Leitura;
import com.piggu.finance.integration.LeitorDeFatura.Linha;
import com.piggu.finance.integration.LeitorDeFatura.TipoDaLinha;
import com.piggu.testing.DuasFamilias;
import com.piggu.testing.PostgresIntegrationTest;
import com.piggu.testing.TokensDeTeste;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Conferir a fatura do cartao: plano, consentimento, conciliacao e reimportacao sem duplicar. */
@AutoConfigureMockMvc
class ConferenciaDaFaturaTest extends PostgresIntegrationTest {

    private static final String ROTA = "/api/expenses/import/card-statement";

    /** Fecha: 30 + 100 + 100 (parcela 3/5) + 6 (IOF) = 236. */
    private static final List<String> FATURA = List.of(
            "Banco Inter",
            "Vencimento 10/12/2026",
            "Total desta fatura R$ 236,00",
            "05/11 PADARIA SOL 30,00",
            "12/11 MERCADO BOM 100,00",
            "05/09 LOJA PARC 03/05 100,00",
            "20/11 IOF 6,00");

    private static final List<String> NAO_FECHA = List.of(
            "Banco Inter",
            "Vencimento 10/12/2026",
            "Total desta fatura R$ 999,00",
            "05/11 PADARIA SOL 30,00",
            "12/11 MERCADO BOM 100,00");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private GeminiFaturaReader gemini;

    private DuasFamilias casas;

    @BeforeEach
    void preparar() {
        casas = new DuasFamilias(mockMvc, json);
        when(gemini.habilitado()).thenReturn(true);
    }

    @Test
    @DisplayName("gratuito: so a contagem, nenhuma linha nem descricao no corpo")
    void gratuitoSoContagem() throws Exception {
        RequestPostProcessor gratuita = gratuita();
        padaria(gratuita);

        String corpo = conferir(gratuita, FATURA, Map.of()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode r = json.readTree(corpo);

        assertThat(r.path("linhas").isNull() || r.path("linhas").isMissingNode()).isTrue();
        assertThat(corpo).doesNotContain("PADARIA", "MERCADO", "LOJA", "IOF", "fatura:");
        assertThat(r.path("contagem").path("compras").asInt()).isEqualTo(3);
        assertThat(r.path("contagem").path("tarifas").asInt()).isEqualTo(1);
        assertThat(r.path("contagem").path("jaLancadas").asInt()).isEqualTo(1);
        assertThat(r.path("contagem").path("faltam").asInt()).isEqualTo(3);
        assertThat(r.path("origem").asString()).isEqualTo("PROPRIA");
        assertThat(r.path("total").decimalValue()).isEqualByComparingTo("236.00");
        verify(gemini, never()).ler(anyString());
    }

    @Test
    @DisplayName("Premium: linhas com situacao, gasto relacionado, categoria, parcela e idExterno")
    void premiumComLinhas() throws Exception {
        String cartao = casas.criar("/api/accounts", casas.casaA,
                "{\"nome\":\"Inter\",\"tipo\":\"CARTAO\",\"fechamento\":1,\"vencimento\":10}").path("id").asString();
        String padaria = padaria(casas.casaA);

        JsonNode r = ler(conferir(casas.casaA, FATURA, Map.of("contaId", cartao)));

        assertThat(r.path("banco").asString()).isEqualTo("inter");
        assertThat(r.path("vencimento").asString()).isEqualTo("2026-12-10");
        assertThat(r.path("somaLida").decimalValue()).isEqualByComparingTo("236.00");
        assertThat(r.path("aviso").isNull()).isTrue();
        JsonNode linhas = r.path("linhas");
        assertThat(linhas.size()).isEqualTo(4);
        JsonNode primeira = linhas.get(0);
        assertThat(primeira.path("descricao").asString()).isEqualTo("PADARIA SOL");
        assertThat(primeira.path("situacao").asString()).isEqualTo("JA_LANCADA");
        assertThat(primeira.path("gastoId").asString()).isEqualTo(padaria);
        assertThat(primeira.path("gastoDescricao").asString()).isEqualTo("Pão");
        assertThat(primeira.path("gastoData").asString()).isEqualTo("2026-11-05");
        assertThat(primeira.path("tipo").asString()).isEqualTo("COMPRA");
        assertThat(primeira.path("idExterno").asString()).startsWith("fatura:");
        JsonNode parcela = linhas.get(2);
        assertThat(parcela.path("parcelaNumero").asInt()).isEqualTo(3);
        assertThat(parcela.path("parcelaTotal").asInt()).isEqualTo(5);
        assertThat(parcela.path("situacao").asString()).isEqualTo("FALTA");
        assertThat(parcela.path("categoria").asString()).isNotBlank();
        assertThat(linhas.get(3).path("tipo").asString()).isEqualTo("TARIFA");
    }

    @Test
    @DisplayName("nao fechou no gratuito: sem linhas e 422 PLANO_PREMIUM; com linhas, a contagem parcial com aviso")
    void naoFechouGratuito422Premium() throws Exception {
        conferir(gratuita(), List.of("Fatura sem compras"), Map.of())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PLANO_PREMIUM"))
                .andExpect(jsonPath("$.erro").value(
                        "Não consegui ler esta fatura sozinho. A leitura pela IA faz parte do Piggu Premium."));

        String corpo = conferir(gratuita(), NAO_FECHA, Map.of()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode r = json.readTree(corpo);
        assertThat(r.path("aviso").asString()).contains("Premium");
        assertThat(r.path("contagem").path("compras").asInt()).isEqualTo(2);
        assertThat(r.path("linhas").isNull() || r.path("linhas").isMissingNode()).isTrue();
        assertThat(corpo).doesNotContain("PADARIA", "MERCADO");
        verify(gemini, never()).ler(anyString());
    }

    @Test
    @DisplayName("PDF sem texto (digitalizado): a mesma mensagem para gratuito e Premium, sem falar em Premium")
    void pdfSemTextoMesmaMensagem() throws Exception {
        String msg = "Não consegui ler esta fatura. Envie o PDF original do banco, não uma foto ou digitalização.";
        conferir(gratuita(), List.of(), Map.of()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value(not("PLANO_PREMIUM")))
                .andExpect(jsonPath("$.erro").value(msg));
        conferir(casas.casaA, List.of(), Map.of()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.erro").value(msg));
        verify(gemini, never()).ler(anyString());
    }

    @Test
    @DisplayName("Premium que nao fechou: sem autorizacao o texto nao vai ao Gemini; autorizado, vai")
    void premiumPedeConsentimento() throws Exception {
        conferir(casas.casaA, NAO_FECHA, Map.of())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value(Consentimentos.CODIGO_NECESSARIO))
                .andExpect(jsonPath("$.erro").value("Para ler esta fatura, o Piggu envia o texto dela (não o PDF) "
                        + "para a IA do Google (Gemini). Autorize o envio para continuar."));
        verify(gemini, never()).ler(anyString());

        when(gemini.ler(anyString())).thenReturn(new Leitura("inter", LocalDate.of(2026, 12, 10),
                new BigDecimal("130.00"), List.of(
                linha("2026-11-05", "PADARIA SOL", "30.00", null, null),
                linha("2026-11-12", "MERCADO BOM", "100.00", null, null))));
        JsonNode r = ler(conferir(casas.casaA, NAO_FECHA, autorizado()));
        assertThat(r.path("origem").asString()).isEqualTo("GEMINI");
        assertThat(r.path("linhas").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("linhas incoerentes de qualquer leitor: valor fora da faixa sai, parcela sem sentido vira a vista")
    void validaAsLinhasDoLeitor() throws Exception {
        when(gemini.ler(anyString())).thenReturn(new Leitura("inter", LocalDate.of(2026, 12, 10),
                new BigDecimal("130.00"), List.of(
                linha("2026-11-05", "ZERO", "0.00", null, null),
                linha("2026-11-05", "CARO DEMAIS", "1000000.01", null, null),
                linha("2026-11-06", "PARCELA AO CONTRARIO", "10.00", 5, 3),
                linha("2026-11-07", "SO NUMERO", "20.00", 2, null),
                linha("2026-11-08", "PARCELA DEMAIS", "30.00", 1, 49),
                linha("2026-11-09", "BOA", "40.00", 2, 4))));

        JsonNode linhas = ler(conferir(casas.casaA, NAO_FECHA, autorizado())).path("linhas");

        assertThat(linhas.findValuesAsString("descricao"))
                .containsExactly("PARCELA AO CONTRARIO", "SO NUMERO", "PARCELA DEMAIS", "BOA");
        for (int i = 0; i < 3; i++) {
            assertThat(linhas.get(i).path("parcelaNumero").isNull()).isTrue();
            assertThat(linhas.get(i).path("parcelaTotal").isNull()).isTrue();
        }
        assertThat(linhas.get(3).path("parcelaNumero").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("Gemini falhou com leitura propria parcial: devolve a parcial com aviso")
    void geminiFalhouFicaAParcial() throws Exception {
        when(gemini.ler(anyString())).thenThrow(new BusinessException("A IA não conseguiu ler a fatura."));

        JsonNode r = ler(conferir(casas.casaA, NAO_FECHA, autorizado()));

        assertThat(r.path("origem").asString()).isEqualTo("PROPRIA");
        assertThat(r.path("aviso").asString()).isNotBlank();
        assertThat(r.path("linhas").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("PDF com senha: sem a senha, 422 PDF_COM_SENHA; com ela, le")
    void pdfComSenha422() throws Exception {
        String cifrado = base64(pdf(FATURA, "1234"));
        enviar(casas.casaA, Map.of("pdfBase64", cifrado))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PDF_COM_SENHA"));
        enviar(casas.casaA, Map.of("pdfBase64", cifrado, "senha", "1234")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("PDF acima de 5 MB ou base64 quebrado: mensagem clara")
    void grandeDemais() throws Exception {
        enviar(casas.casaA, Map.of("pdfBase64", base64(new byte[5 * 1024 * 1024 + 1])))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.erro").value("PDF grande demais (máximo 5 MB)."));
        enviar(casas.casaA, Map.of("pdfBase64", "@@@nao e base64@@@"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.erro").value("O PDF chegou corrompido. Tente de novo."));
    }

    @Test
    @DisplayName("ler a mesma fatura depois de importar tudo: tudo ja lancado, nada duplica")
    void lerDeNovoDepoisDeImportarTudoJaLancada() throws Exception {
        String cartao = casas.criar("/api/accounts", casas.casaA,
                "{\"nome\":\"Inter\",\"tipo\":\"CARTAO\",\"fechamento\":1,\"vencimento\":10}").path("id").asString();
        JsonNode linhas = ler(conferir(casas.casaA, FATURA, Map.of("contaId", cartao))).path("linhas");

        List<Map<String, Object>> escolhidas = new java.util.ArrayList<>();
        linhas.forEach(l -> {
            Map<String, Object> linha = new HashMap<>();
            linha.put("data", l.path("data").asString());
            linha.put("descricao", l.path("descricao").asString());
            linha.put("valor", l.path("valor").decimalValue());
            linha.put("idExterno", l.path("idExterno").asString());
            linha.put("categoria", l.path("categoria").asString());
            if (!l.path("parcelaNumero").isNull()) {
                linha.put("parcelaNumero", l.path("parcelaNumero").asInt());
                linha.put("parcelaTotal", l.path("parcelaTotal").asInt());
            }
            escolhidas.add(linha);
        });
        mockMvc.perform(post("/api/expenses/import").with(casas.casaA).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("contaId", cartao, "linhas", escolhidas))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importados").value(4));

        JsonNode r = ler(conferir(casas.casaA, FATURA, Map.of("contaId", cartao)));
        assertThat(r.path("contagem").path("jaLancadas").asInt()).isEqualTo(4);
        assertThat(r.path("contagem").path("faltam").asInt()).isZero();
        assertThat(r.path("contagem").path("confira").asInt()).isZero();
        // 3 a vista + 3 parcelas (3/5, 4/5, 5/5)
        assertThat(ler(mockMvc.perform(get("/api/expenses").with(casas.casaA))).size()).isEqualTo(6);
    }

    @Test
    @DisplayName("gasto de outra familia nunca casa; cartao de outra familia ou conta que nao e cartao: 404")
    void isolamentoEntreFamilias() throws Exception {
        padaria(casas.casaB);
        String cartaoDeB = casas.criar("/api/accounts", casas.casaB,
                "{\"nome\":\"Inter\",\"tipo\":\"CARTAO\",\"fechamento\":1,\"vencimento\":10}").path("id").asString();
        String contaDeA = casas.criar("/api/accounts", casas.casaA,
                "{\"nome\":\"Conta\",\"tipo\":\"CONTA\"}").path("id").asString();

        JsonNode r = ler(conferir(casas.casaA, FATURA, Map.of()));
        assertThat(r.path("contagem").path("jaLancadas").asInt()).isZero();
        assertThat(r.path("linhas").get(0).path("situacao").asString()).isEqualTo("FALTA");
        assertThat(r.path("linhas").get(0).path("gastoId").isNull()).isTrue();

        for (String conta : List.of(cartaoDeB, contaDeA, UUID.randomUUID().toString())) {
            conferir(casas.casaA, FATURA, Map.of("contaId", conta))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.erro").value("Conta ou cartao nao encontrado."));
        }
    }

    @Test
    @DisplayName("sem cartao: parcela lancada a mao (10x desde marco) casa pela data esperada da 9/10")
    void semCartaoParcelaComCompraAVista() throws Exception {
        parcelado(casas.casaA, "2026-03-15", 500);

        JsonNode r = ler(conferir(casas.casaA, List.of("Vencimento 10/12/2026", "Total desta fatura R$ 80,00",
                "01/11 PADARIA 30,00", "15/03 LOJA 09/10 50,00"), Map.of()));

        assertThat(r.path("linhas").get(1).path("situacao").asString()).isEqualTo("JA_LANCADA");
        assertThat(r.path("linhas").get(1).path("gastoData").asString()).isEqualTo("2026-11-15");
    }

    @Test
    @DisplayName("sem cartao e so parcelas (compras de marco e junho): as duas ja lancadas")
    void semCartaoSoParcelas() throws Exception {
        parcelado(casas.casaA, "2026-03-15", 500);
        parcelado(casas.casaA, "2026-06-20", 400);

        JsonNode r = ler(conferir(casas.casaA, List.of("Vencimento 10/12/2026", "Total desta fatura R$ 90,00",
                "15/03 LOJA A 09/10 50,00", "20/06 LOJA B 06/10 40,00"), Map.of()));

        assertThat(r.path("contagem").path("jaLancadas").asInt()).isEqualTo(2);
        assertThat(r.path("contagem").path("faltam").asInt()).isZero();
        // a 9/10 e a 6/10, nunca a 1/10 do mesmo valor na data original da compra
        assertThat(r.path("linhas").findValuesAsString("gastoData")).containsExactly("2026-11-15", "2026-11-20");
    }

    @Test
    @DisplayName("Gemini tambem nao fechou: fica a leitura mais perto do total (aqui a propria)")
    void geminiQueTambemNaoFechaFicaAMaisPerto() throws Exception {
        when(gemini.ler(anyString())).thenReturn(new Leitura("inter", LocalDate.of(2026, 12, 10),
                new BigDecimal("999.00"), List.of(linha("2026-11-05", "PADARIA SOL", "30.00", null, null))));

        JsonNode r = ler(conferir(casas.casaA, NAO_FECHA, autorizado()));

        assertThat(r.path("origem").asString()).isEqualTo("PROPRIA");
        assertThat(r.path("linhas").size()).isEqualTo(2);
        assertThat(r.path("aviso").asString()).isNotBlank();
    }

    private void parcelado(RequestPostProcessor quem, String data, int valor) throws Exception {
        casas.criar("/api/expenses", quem, "{\"data\":\"" + data + "\",\"parcelas\":10,"
                + "\"itens\":[{\"item\":\"Loja\",\"categoria\":\"Outros\",\"valor\":" + valor + "}]}");
    }

    private static RequestPostProcessor gratuita() {
        UUID familia = UUID.randomUUID();
        return TokensDeTeste.gratuitaDa(familia, "gratuita-" + familia + "@piggu.test", PigguRole.TITULAR);
    }

    private static Map<String, Object> autorizado() {
        return Map.of("autorizoIa", true, "versaoDoAviso", Consentimentos.VERSAO_DO_AVISO);
    }

    private static Linha linha(String data, String descricao, String valor, Integer n, Integer m) {
        return new Linha(LocalDate.parse(data), descricao, new BigDecimal(valor), TipoDaLinha.COMPRA, n, m,
                "fatura:" + descricao.hashCode());
    }

    private String padaria(RequestPostProcessor quem) throws Exception {
        return casas.criar("/api/expenses", quem,
                "{\"data\":\"2026-11-05\",\"itens\":[{\"item\":\"Pão\",\"categoria\":\"Alimentação\",\"valor\":30}]}")
                .get(0).path("id").asString();
    }

    private ResultActions conferir(RequestPostProcessor quem, List<String> texto, Map<String, Object> extra)
            throws Exception {
        Map<String, Object> corpo = new HashMap<>(extra);
        corpo.put("pdfBase64", base64(pdf(texto, null)));
        return enviar(quem, corpo);
    }

    private ResultActions enviar(RequestPostProcessor quem, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(post(ROTA).with(quem).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(corpo)));
    }

    private JsonNode ler(ResultActions resposta) throws Exception {
        return json.readTree(resposta.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] pdf(List<String> linhas, String senha) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            doc.addPage(pagina);
            try (PDPageContentStream c = new PDPageContentStream(doc, pagina)) {
                c.beginText();
                c.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                c.newLineAtOffset(50, 720);
                for (String l : linhas) {
                    c.showText(l);
                    c.newLineAtOffset(0, -16);
                }
                c.endText();
            }
            if (senha != null) {
                StandardProtectionPolicy politica = new StandardProtectionPolicy(senha, senha, new AccessPermission());
                politica.setEncryptionKeyLength(128);
                doc.protect(politica);
            }
            doc.save(saida);
            return saida.toByteArray();
        }
    }
}
