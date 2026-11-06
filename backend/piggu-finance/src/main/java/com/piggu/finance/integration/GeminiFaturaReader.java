package com.piggu.finance.integration;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UpstreamException;
import com.piggu.common.web.Texto;
import com.piggu.finance.config.IntegracoesProperties;
import com.piggu.finance.integration.LeitorDeFatura.Leitura;
import com.piggu.finance.integration.LeitorDeFatura.Linha;
import com.piggu.finance.integration.LeitorDeFatura.TipoDaLinha;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reserva da leitura de fatura: quando as regras do {@link LeitorDeFatura} nao fecham o total,
 * manda ao Gemini so o TEXTO extraido do PDF (nunca o arquivo) e recebe a mesma {@link Leitura}.
 * Linha sem data ou valor validos, ou com tipo desconhecido, e descartada: nunca se adivinha.
 * Nem o texto da fatura nem a resposta vao para o log.
 */
@Service
public class GeminiFaturaReader {

    private static final Logger log = LoggerFactory.getLogger(GeminiFaturaReader.class);

    private static final String PROMPT = String.join(System.lineSeparator(),
            "Leia o texto desta fatura de cartao de credito.",
            "Devolva o banco, o vencimento (AAAA-MM-DD), o total da fatura e todas as linhas de lancamento.",
            "Cada linha tem data (AAAA-MM-DD), descricao sem o sufixo de parcela, valor positivo,",
            "tipo (COMPRA, TARIFA ou IGNORADA) e, se for parcelada, parcelaNumero e parcelaTotal.",
            "Use IGNORADA para pagamento, saldo anterior, credito e estorno; TARIFA para anuidade, juros e IOF.",
            "Nunca invente linhas ou valores que nao estejam no texto.");

    private final RestClient cliente;
    private final IntegracoesProperties.Gemini propriedades;
    private final ObjectMapper json;

    public GeminiFaturaReader(RestClient.Builder builder, IntegracoesProperties propriedades, ObjectMapper json) {
        this.propriedades = propriedades.gemini();
        this.cliente = builder.baseUrl(this.propriedades.baseUrl()).build();
        this.json = json;
    }

    public boolean habilitado() {
        return propriedades.habilitado();
    }

    public Leitura ler(String texto) {
        if (!propriedades.habilitado()) {
            throw new BusinessException("A leitura de fatura por IA não está configurada.");
        }
        JsonNode lido = desserializar(chamarGemini(texto));
        List<Linha> linhas = new ArrayList<>();
        lido.path("linhas").forEach(n -> {
            Linha linha = linha(n);
            if (linha != null) {
                linhas.add(linha);
            }
        });
        String banco = Texto.limitar(lido.path("banco").asString(""), 60);
        return new Leitura(banco.isEmpty() ? null : banco,
                data(lido.path("vencimento")), valor(lido.path("total")),
                LeitorDeFatura.comIdsGerados(linhas));
    }

    private String chamarGemini(String texto) {
        String caminho = "/v1beta/models/" + propriedades.modelo() + ":generateContent";
        try {
            JsonNode resposta = cliente.post()
                    .uri(caminho)
                    .header("x-goog-api-key", propriedades.apiKey())
                    .header("Content-Type", "application/json")
                    .body(Map.of(
                            "contents", List.of(Map.of("parts", List.of(
                                    Map.of("text", PROMPT),
                                    Map.of("text", texto)))),
                            "generationConfig", Map.of(
                                    "responseMimeType", "application/json",
                                    "responseSchema", esquema())))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        log.error("Falha Gemini na fatura ({})", res.getStatusCode());
                        throw GeminiReceiptReader.traduzirErro(res.getStatusCode().value(), "a fatura");
                    })
                    .body(JsonNode.class);
            return extrairTexto(resposta);
        } catch (BusinessException erro) {
            throw erro;
        } catch (RuntimeException erro) {
            log.error("Não foi possível falar com o Gemini (fatura)", erro);
            throw new UpstreamException("Não consegui ler a fatura agora. Tente de novo em instantes.");
        }
    }

    private static String extrairTexto(JsonNode resposta) {
        StringBuilder texto = new StringBuilder();
        if (resposta != null) {
            resposta.path("candidates").path(0).path("content").path("parts").forEach(parte -> {
                if (parte.hasNonNull("text") && !parte.path("thought").asBoolean(false)) {
                    texto.append(parte.path("text").asString());
                }
            });
        }
        if (texto.isEmpty()) {
            throw new UpstreamException("A IA não conseguiu ler a fatura.");
        }
        return texto.toString();
    }

    private JsonNode desserializar(String texto) {
        try {
            return json.readTree(texto);
        } catch (Exception erro) {
            // So o tamanho: a resposta carrega os gastos da pessoa.
            log.error("Resposta do Gemini fora do formato esperado ({} caracteres)", texto.length());
            throw new UpstreamException("A IA devolveu uma resposta que não consegui entender.");
        }
    }

    private static Linha linha(JsonNode n) {
        LocalDate data = data(n.path("data"));
        BigDecimal valor = valor(n.path("valor"));
        String descricao = Texto.limitar(n.path("descricao").asString(""), 200);
        TipoDaLinha tipo;
        try {
            tipo = TipoDaLinha.valueOf(n.path("tipo").asString("").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException erro) {
            return null;
        }
        if (data == null || valor == null || descricao.isEmpty()) {
            return null;
        }
        return new Linha(data, descricao, valor, tipo, inteiro(n.path("parcelaNumero")),
                inteiro(n.path("parcelaTotal")), null);
    }

    private static Integer inteiro(JsonNode n) {
        return n.isNumber() && n.intValue() > 0 ? n.intValue() : null;
    }

    private static LocalDate data(JsonNode n) {
        try {
            return LocalDate.parse(n.asString("").trim());
        } catch (RuntimeException erro) {
            return null;
        }
    }

    /** Valor em modulo (sinal vem do tipo); aceita numero ou texto "1234.56". */
    private static BigDecimal valor(JsonNode n) {
        try {
            return new BigDecimal(n.asString("").trim()).abs();
        } catch (NumberFormatException erro) {
            return null;
        }
    }

    private static Map<String, Object> esquema() {
        Map<String, Object> texto = Map.of("type", "string");
        Map<String, Object> numero = Map.of("type", "number");
        Map<String, Object> inteiro = Map.of("type", "integer");
        Map<String, Object> linha = Map.of("type", "object",
                "properties", Map.of("data", texto, "descricao", texto, "valor", numero,
                        "tipo", Map.of("type", "string", "enum", List.of("COMPRA", "TARIFA", "IGNORADA")),
                        "parcelaNumero", inteiro, "parcelaTotal", inteiro),
                "required", List.of("data", "descricao", "valor", "tipo"));
        return Map.of("type", "object",
                "properties", Map.of("banco", texto, "vencimento", texto, "total", numero,
                        "linhas", Map.of("type", "array", "items", linha)),
                "required", List.of("banco", "vencimento", "total", "linhas"));
    }
}
