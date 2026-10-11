package com.piggu.finance.assistente;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UpstreamException;
import com.piggu.finance.config.IntegracoesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Uma pergunta ao Gemini com function calling: o modelo pede ferramentas, o Piggu
 * executa e devolve, ate o modelo responder em texto.
 *
 * <p>O conteudo do modelo volta inteiro na rodada seguinte (os modelos com raciocinio
 * pedem a assinatura do pensamento de volta). Nada da conversa vai para o log: so o
 * numero de rodadas e o nome das ferramentas.</p>
 */
@Component
public class ConversaComGemini {

    /** Rodadas de ferramenta por pergunta; passou disso, o modelo se perdeu. */
    static final int MAXIMO_DE_RODADAS = 5;

    private static final Logger log = LoggerFactory.getLogger(ConversaComGemini.class);

    /** Executa a ferramenta pedida e devolve o resultado como objeto JSON. */
    @FunctionalInterface
    public interface Executor {
        Object executar(String nome, JsonNode argumentos);
    }

    /** Uma fala anterior: {@code daPessoa} falso e o que o Piggu respondeu. */
    public record Fala(boolean daPessoa, String texto) {
    }

    private final RestClient cliente;
    private final IntegracoesProperties.Gemini propriedades;
    private final ObjectMapper json;

    public ConversaComGemini(RestClient.Builder builder, IntegracoesProperties propriedades, ObjectMapper json) {
        this.propriedades = propriedades.gemini();
        this.cliente = builder.baseUrl(this.propriedades.baseUrl()).build();
        this.json = json;
    }

    public boolean habilitado() {
        return propriedades.habilitado();
    }

    public String responder(String instrucoes, List<Fala> historico, String pergunta,
                            List<Map<String, Object>> declaracoes, Executor executor) {
        List<Object> conteudos = new ArrayList<>();
        historico.forEach(fala -> conteudos.add(texto(fala.daPessoa() ? "user" : "model", fala.texto())));
        conteudos.add(texto("user", pergunta));

        for (int rodada = 1; rodada <= MAXIMO_DE_RODADAS; rodada++) {
            JsonNode conteudo = chamar(instrucoes, conteudos, declaracoes);
            List<Object> respostas = new ArrayList<>();
            StringBuilder textoFinal = new StringBuilder();
            for (JsonNode parte : conteudo.path("parts")) {
                if (parte.has("functionCall")) {
                    JsonNode chamada = parte.path("functionCall");
                    String nome = chamada.path("name").asString("");
                    log.info("Assistente usou a ferramenta {} (rodada {})", nome, rodada);
                    Map<String, Object> resposta = new LinkedHashMap<>();
                    if (chamada.hasNonNull("id")) {
                        resposta.put("id", chamada.path("id").asString());
                    }
                    resposta.put("name", nome);
                    resposta.put("response", Map.of("resultado", executor.executar(nome, chamada.path("args"))));
                    respostas.add(Map.of("functionResponse", resposta));
                } else if (parte.hasNonNull("text") && !parte.path("thought").asBoolean(false)) {
                    textoFinal.append(parte.path("text").asString());
                }
            }
            if (respostas.isEmpty()) {
                if (textoFinal.isEmpty()) {
                    throw new UpstreamException("A IA nao respondeu agora. Tente de novo.");
                }
                return textoFinal.toString().strip();
            }
            conteudos.add(conteudo);
            conteudos.add(Map.of("role", "user", "parts", respostas));
        }
        log.warn("Assistente passou de {} rodadas de ferramenta", MAXIMO_DE_RODADAS);
        throw new UpstreamException("Nao consegui chegar numa resposta. Tente perguntar de outro jeito.");
    }

    private JsonNode chamar(String instrucoes, List<Object> conteudos, List<Map<String, Object>> declaracoes) {
        Map<String, Object> corpo = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", instrucoes))),
                "contents", conteudos,
                "tools", List.of(Map.of("functionDeclarations", declaracoes)),
                "generationConfig", Map.of("temperature", 0.2));
        try {
            JsonNode resposta = cliente.post()
                    // Chave no cabecalho, nunca na URL (a URL entra nas mensagens de erro).
                    .uri("/v1beta/models/" + propriedades.modelo() + ":generateContent")
                    .header("x-goog-api-key", propriedades.apiKey())
                    .header("Content-Type", "application/json")
                    .body(json.writeValueAsString(corpo))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        log.error("Falha Gemini no assistente ({})", res.getStatusCode());
                        throw traduzirErro(res.getStatusCode().value());
                    })
                    .body(JsonNode.class);
            JsonNode conteudo = resposta == null ? null : resposta.path("candidates").path(0).path("content");
            if (conteudo == null || conteudo.isMissingNode() || !conteudo.path("parts").isArray()) {
                throw new UpstreamException("A IA nao respondeu agora. Tente de novo.");
            }
            return conteudo;
        } catch (BusinessException erro) {
            throw erro;
        } catch (RuntimeException erro) {
            log.error("Nao foi possivel falar com o Gemini no assistente", erro);
            throw new UpstreamException("O assistente nao respondeu agora. Tente de novo em instantes.");
        }
    }

    private static Map<String, Object> texto(String papel, String texto) {
        return Map.of("role", papel, "parts", List.of(Map.of("text", texto)));
    }

    private static RuntimeException traduzirErro(int codigo) {
        return switch (codigo) {
            case 403 -> new UpstreamException("A chave do Gemini foi recusada. Confira a configuracao.");
            case 404 -> new UpstreamException("O modelo do Gemini nao esta disponivel para esta chave.");
            case 429 -> new UpstreamException("A cota do Gemini acabou por enquanto. Tente mais tarde.");
            default -> new UpstreamException("O assistente nao respondeu agora (erro " + codigo + ").");
        };
    }
}
