package com.piggu.finance.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UpstreamException;
import com.piggu.common.web.Texto;
import com.piggu.finance.api.dto.ReceiptParseRequest;
import com.piggu.finance.api.dto.ReceiptParseResponse;
import com.piggu.finance.config.IntegracoesProperties;
import com.piggu.finance.domain.CategoryService;
import com.piggu.finance.domain.ProductMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Leitura de recibos por foto, com o Gemini.
 *
 * <p>Porte de parseRecibo_. Nada e gravado aqui: a resposta volta para a tela, o
 * usuario confere e so entao envia para POST /api/expenses. Era assim no original
 * e continua sendo, porque a IA erra e o gasto precisa de conferencia humana.</p>
 *
 * <p>A memoria de produtos entra no prompt para padronizar nomes e categorias entre
 * compras do mesmo item.</p>
 */
@Service
public class GeminiReceiptReader {

    private static final Logger log = LoggerFactory.getLogger(GeminiReceiptReader.class);
    private static final int PRODUTOS_NA_MEMORIA = 80;

    private static final Set<String> TIPOS_DE_IMAGEM = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");
    private static final String TIPO_PADRAO = "image/jpeg";

    private final RestClient cliente;
    private final IntegracoesProperties.Gemini propriedades;
    private final CategoryService categorias;
    private final ProductMemoryService memoriaDeProdutos;
    private final ObjectMapper json;

    public GeminiReceiptReader(RestClient.Builder builder,
                               IntegracoesProperties propriedades,
                               CategoryService categorias,
                               ProductMemoryService memoriaDeProdutos,
                               ObjectMapper json) {
        this.propriedades = propriedades.gemini();
        this.cliente = builder.baseUrl(this.propriedades.baseUrl()).build();
        this.categorias = categorias;
        this.memoriaDeProdutos = memoriaDeProdutos;
        this.json = json;
    }

    public ReceiptParseResponse ler(ReceiptParseRequest pedido) {
        if (!propriedades.habilitado()) {
            throw new BusinessException(
                    "A leitura de recibos por foto nao esta configurada. Adicione os itens a mao.");
        }

        LocalDate hoje = LocalDate.now();
        String resposta = chamarGemini(pedido, hoje);
        JsonNode lido = desserializar(resposta);

        return new ReceiptParseResponse(
                UUID.randomUUID(),
                Texto.limitar(lido.path("estabelecimento").asText(""), 200),
                dataOuHoje(lido.path("data").asText(""), hoje),
                extrairItens(lido.path("itens"))
        );
    }

    private String chamarGemini(ReceiptParseRequest pedido, LocalDate hoje) {
        String caminho = "/v1beta/models/" + propriedades.modelo() + ":generateContent";
        try {
            JsonNode resposta = cliente.post()
                    .uri(uri -> uri.path(caminho).queryParam("key", propriedades.apiKey()).build())
                    .header("Content-Type", "application/json")
                    .body(montarCorpo(pedido, hoje))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        log.error("Falha Gemini ({})", res.getStatusCode());
                        throw traduzirErro(res.getStatusCode().value());
                    })
                    .body(JsonNode.class);

            return extrairTexto(resposta);
        } catch (BusinessException erro) {
            // Erro ja traduzido para o usuario em traduzirErro: repassa sem reescrever.
            throw erro;
        } catch (RuntimeException erro) {
            log.error("Nao foi possivel falar com o Gemini", erro);
            throw new UpstreamException("Nao consegui ler o recibo agora. Tente de novo em instantes.");
        }
    }

    private Map<String, Object> montarCorpo(ReceiptParseRequest pedido, LocalDate hoje) {
        String informado = Texto.email(pedido.mimeType());
        String tipoImagem = TIPOS_DE_IMAGEM.contains(informado) ? informado : TIPO_PADRAO;

        return Map.of(
                "contents", List.of(Map.of("parts", List.of(
                        Map.of("text", montarPrompt(hoje)),
                        Map.of("inline_data", Map.of(
                                "mime_type", tipoImagem,
                                "data", pedido.imageBase64()))
                ))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", esquemaDaResposta())
        );
    }

    private String montarPrompt(LocalDate hoje) {
        List<String> memoria = memoriaDeProdutos.memoriaParaIa(PRODUTOS_NA_MEMORIA);
        List<String> linhas = new ArrayList<>(List.of(
                "Leia esta foto de um recibo ou nota fiscal.",
                "Extraia cada item e classifique-o em: " + String.join(", ", categorias.listar()) + ".",
                "Valores devem ser numeros em euro, sem simbolo.",
                "Data no formato AAAA-MM-DD. Se nao conseguir ler, use " + hoje + ".",
                "Se nao der para separar os itens, devolva um item com o total e categoria Outros.",
                "Nunca invente itens ou valores que nao estejam visiveis."
        ));
        if (!memoria.isEmpty()) {
            linhas.add("Use esta memoria para padronizar nomes e categorias quando houver "
                    + "correspondencia: " + String.join("; ", memoria));
        }
        return String.join(System.lineSeparator(), linhas);
    }

    /** Obriga o modelo a devolver exatamente a estrutura que sabemos processar. */
    private Map<String, Object> esquemaDaResposta() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "estabelecimento", Map.of("type", "string"),
                        "data", Map.of("type", "string"),
                        "itens", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "item", Map.of("type", "string"),
                                                "categoria", Map.of("type", "string"),
                                                "valor", Map.of("type", "number")),
                                        "required", List.of("item", "categoria", "valor")))),
                "required", List.of("estabelecimento", "data", "itens")
        );
    }

    private String extrairTexto(JsonNode resposta) {
        if (resposta == null) {
            throw new UpstreamException("A IA nao respondeu.");
        }
        JsonNode partes = resposta.path("candidates").path(0).path("content").path("parts");
        StringBuilder texto = new StringBuilder();
        partes.forEach(parte -> {
            // Modelos com raciocinio marcam blocos internos com thought; esses nao sao resposta.
            if (parte.hasNonNull("text") && !parte.path("thought").asBoolean(false)) {
                texto.append(parte.path("text").asText());
            }
        });
        if (texto.isEmpty()) {
            throw new BusinessException("A IA nao encontrou itens no recibo.");
        }
        return texto.toString();
    }

    private JsonNode desserializar(String texto) {
        try {
            return json.readTree(texto);
        } catch (Exception erro) {
            log.error("Resposta do Gemini fora do formato esperado: {}", texto, erro);
            throw new UpstreamException("A IA devolveu uma resposta que nao consegui entender.");
        }
    }

    private List<ReceiptParseResponse.Item> extrairItens(JsonNode itens) {
        List<ReceiptParseResponse.Item> resultado = new ArrayList<>();
        itens.forEach(item -> {
            String nome = Texto.limitar(item.path("item").asText(""), 200);
            BigDecimal valor = item.path("valor").decimalValue();
            if (nome.isEmpty() || valor.compareTo(BigDecimal.ZERO) < 0) {
                return;
            }
            resultado.add(new ReceiptParseResponse.Item(
                    nome,
                    categorias.normalizar(item.path("categoria").asText("")),
                    valor));
        });
        if (resultado.isEmpty()) {
            throw new BusinessException("A IA nao encontrou itens no recibo.");
        }
        return resultado;
    }

    private LocalDate dataOuHoje(String valor, LocalDate hoje) {
        try {
            return valor.isBlank() ? hoje : LocalDate.parse(valor);
        } catch (DateTimeParseException erro) {
            return hoje;
        }
    }

    /** Mesmas mensagens por codigo que o Apps Script ja devolvia. */
    private RuntimeException traduzirErro(int codigo) {
        return switch (codigo) {
            case 400 -> new BusinessException("O Gemini recusou o formato da imagem ou do pedido.");
            case 403 -> new UpstreamException("A chave do Gemini foi recusada. Confira a configuracao.");
            case 404 -> new UpstreamException("O modelo do Gemini nao esta disponivel para esta chave.");
            case 429 -> new UpstreamException("A cota do Gemini acabou por enquanto. Tente mais tarde.");
            default -> new UpstreamException("O Gemini nao conseguiu ler o recibo agora (erro " + codigo + ").");
        };
    }
}
