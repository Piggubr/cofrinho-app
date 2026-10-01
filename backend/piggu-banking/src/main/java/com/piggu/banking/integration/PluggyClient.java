package com.piggu.banking.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.piggu.banking.config.PluggyProperties;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.error.UpstreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Cliente da API da Pluggy (https://api.pluggy.ai).
 *
 * <p>A Pluggy autentica em dois passos: clientId e clientSecret geram uma apiKey
 * valida por duas horas, e e ela que vai no cabecalho X-API-KEY das demais chamadas.
 * A apiKey fica guardada e e renovada um pouco antes de vencer.</p>
 *
 * <p>As credenciais do banco do usuario nunca passam por aqui: ele as digita no
 * widget Pluggy Connect, que fala direto com a Pluggy. O backend so ve o id do item
 * conectado.</p>
 */
@Component
public class PluggyClient {

    private static final Logger log = LoggerFactory.getLogger(PluggyClient.class);

    /** A Pluggy da duas horas; renovar dez minutos antes evita usar uma chave no limite. */
    private static final Duration VALIDADE_DA_CHAVE = Duration.ofMinutes(110);

    private final RestClient cliente;
    private final PluggyProperties propriedades;
    private final Clock relogio;

    private String apiKey;
    private Instant chaveVenceEm = Instant.MIN;

    @Autowired
    public PluggyClient(RestClient.Builder builder, PluggyProperties propriedades) {
        this(builder, propriedades, Clock.systemUTC());
    }

    PluggyClient(RestClient.Builder builder, PluggyProperties propriedades, Clock relogio) {
        this.propriedades = propriedades;
        this.cliente = builder.baseUrl(propriedades.baseUrl()).build();
        this.relogio = relogio;
    }

    /** Item conectado na Pluggy: a ligacao entre um usuario e uma instituicao. */
    public record Item(String id, String status, String clientUserId, String instituicao) {
    }

    /** Conta de um item, com o saldo do momento da ultima atualizacao na Pluggy. */
    public record Conta(String id, String nome, String tipo, String numero, BigDecimal saldo, String moeda) {
    }

    /**
     * Token de 30 minutos que abre o widget Pluggy Connect.
     *
     * @param clientUserId gravado no item criado; e com ele que o registro confere o dono
     */
    public String criarConnectToken(String clientUserId) {
        JsonNode resposta = chamar(() -> cliente.post().uri("/connect_token")
                .header("X-API-KEY", apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("options", Map.of("clientUserId", clientUserId))));
        return resposta.path("accessToken").asText();
    }

    public Item buscarItem(String itemId) {
        JsonNode item = chamar(() -> cliente.get().uri("/items/{id}", itemId)
                .header("X-API-KEY", apiKey()));
        return new Item(
                item.path("id").asText(),
                item.path("status").asText(""),
                item.path("clientUserId").asText(""),
                item.path("connector").path("name").asText("")
        );
    }

    /**
     * Apaga o item na Pluggy: ela para de sincronizar e descarta as credenciais e os
     * dados daquele banco. E a revogacao do consentimento (art. 15 da RC 1/2020).
     */
    public void apagarItem(String itemId) {
        chamar(() -> cliente.delete().uri("/items/{id}", itemId)
                .header("X-API-KEY", apiKey()));
    }

    // ponytail: le so a primeira pagina (20 contas por item); paginar se aparecer item com mais.
    public List<Conta> listarContas(String itemId) {
        JsonNode resposta = chamar(() -> cliente.get().uri("/accounts?itemId={id}", itemId)
                .header("X-API-KEY", apiKey()));
        List<Conta> contas = new ArrayList<>();
        resposta.path("results").forEach(conta -> contas.add(new Conta(
                conta.path("id").asText(),
                conta.path("marketingName").asText(conta.path("name").asText("")),
                conta.path("subtype").asText(conta.path("type").asText("")),
                conta.path("number").asText(""),
                conta.path("balance").decimalValue(),
                conta.path("currencyCode").asText("BRL")
        )));
        return contas;
    }

    private synchronized String apiKey() {
        Instant agora = relogio.instant();
        if (apiKey != null && agora.isBefore(chaveVenceEm)) {
            return apiKey;
        }
        JsonNode resposta = chamar(() -> cliente.post().uri("/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("clientId", propriedades.clientId(), "clientSecret", propriedades.clientSecret())));
        apiKey = resposta.path("apiKey").asText();
        chaveVenceEm = agora.plus(VALIDADE_DA_CHAVE);
        return apiKey;
    }

    private JsonNode chamar(Supplier<RestClient.RequestHeadersSpec<?>> pedido) {
        if (!propriedades.habilitado()) {
            throw new BusinessException("A conexao com bancos nao esta configurada.");
        }
        try {
            return pedido.get().retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw traduzirErro(res.getStatusCode().value());
                    })
                    .body(JsonNode.class);
        } catch (BusinessException erro) {
            throw erro;
        } catch (RuntimeException erro) {
            log.error("Falha ao consultar a Pluggy", erro);
            throw new UpstreamException("O servico de conexao com bancos esta indisponivel agora.");
        }
    }

    private RuntimeException traduzirErro(int codigo) {
        if (codigo == 401 || codigo == 403) {
            synchronized (this) {
                apiKey = null;
            }
            return new UpstreamException("A credencial da Pluggy foi recusada. Confira a configuracao.");
        }
        if (codigo == 404) {
            return new NotFoundException("Conexao bancaria nao encontrada na Pluggy.");
        }
        return new UpstreamException("A Pluggy nao respondeu corretamente (erro " + codigo + ").");
    }
}
