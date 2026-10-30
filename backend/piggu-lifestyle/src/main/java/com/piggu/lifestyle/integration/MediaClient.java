package com.piggu.lifestyle.integration;

import com.piggu.common.error.ApiError;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UpstreamException;
import com.piggu.common.web.CorrelacaoFilter;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

/**
 * Conversa com o piggu-media para guardar e apagar as fotos dos lugares.
 *
 * <p>Este e o unico ponto do sistema em que um servico chama outro. Foi uma escolha
 * deliberada: arquivos binarios ficam todos sob um dono so, em vez de cada servico
 * falar com o Google Drive por conta propria.</p>
 *
 * <p>O token de quem fez a chamada e repassado adiante, entao o media aplica as
 * mesmas regras de permissao que aplicaria em uma chamada direta do navegador.</p>
 */
@Component
public class MediaClient {

    private static final Logger log = LoggerFactory.getLogger(MediaClient.class);

    private final RestClient cliente;

    public MediaClient(RestClient.Builder builder, @Value("${piggu.servicos.media-url}") String baseUrl) {
        this.cliente = builder.baseUrl(baseUrl)
                // Mesma requisicao nos logs dos dois servicos.
                .requestInterceptor((pedido, corpo, execucao) -> {
                    String id = MDC.get(CorrelacaoFilter.MDC_REQUISICAO);
                    if (id != null) {
                        pedido.getHeaders().set(CorrelacaoFilter.CABECALHO, id);
                    }
                    return execucao.execute(pedido, corpo);
                })
                .build();
    }

    /**
     * Envia uma foto e devolve o identificador do arquivo guardado.
     *
     * @param contexto rotulo do dono da imagem, usado pelo media para organizar as pastas
     */
    @CircuitBreaker(name = "media", fallbackMethod = "falhaAoEnviar")
    public UUID enviar(String imageBase64, String mimeType, String contexto) {
        JsonNode resposta;
        try {
            resposta = cliente.post()
                    .uri("/api/assets")
                    .header(HttpHeaders.AUTHORIZATION, autorizacao())
                    .body(Map.of(
                            "imageBase64", imageBase64,
                            "mimeType", mimeType == null ? "image/jpeg" : mimeType,
                            "contexto", contexto))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException recusada) {
            // Foto recusada (tipo, tamanho, limite da familia): o motivo do media vai para a tela, e tentar
            // de novo nao adianta. Nao conta como media fora do ar (ignoreExceptions do circuito).
            ApiError corpo = corpoDe(recusada);
            throw new BusinessException(corpo != null && corpo.erro() != null ? corpo.erro() : "A foto foi recusada.",
                    HttpStatus.valueOf(recusada.getStatusCode().value()), corpo == null ? null : corpo.codigo());
        }

        if (resposta == null || !resposta.hasNonNull("id")) {
            throw new UpstreamException("Nao consegui guardar a foto agora.");
        }
        return UUID.fromString(resposta.path("id").asString());
    }

    /**
     * Apaga uma foto.
     *
     * <p>A falha aqui e registrada mas nao propagada: se o arquivo ficar orfao no
     * Drive, isso e bem menos grave do que impedir o usuario de apagar o lugar.
     * O Apps Script tomava a mesma decisao, com um try em volta do setTrashed.</p>
     */
    public void apagar(UUID assetId) {
        if (assetId == null) {
            return;
        }
        try {
            cliente.delete()
                    .uri("/api/assets/{id}", assetId)
                    .header(HttpHeaders.AUTHORIZATION, autorizacao())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException erro) {
            log.warn("Nao foi possivel apagar a foto {} no servico de media", assetId, erro);
        }
    }

    /** O ApiError do media; nulo quando a recusa veio de outro lugar (proxy) e o corpo nao e JSON. */
    private static ApiError corpoDe(HttpClientErrorException recusada) {
        try {
            return recusada.getResponseBodyAs(ApiError.class);
        } catch (RestClientException semJson) {
            return null;
        }
    }

    private String autorizacao() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof Jwt jwt) {
            return "Bearer " + jwt.getTokenValue();
        }
        throw new UpstreamException("Sessao ausente para falar com o servico de fotos.");
    }

    /** Chamado pelo circuito; visivel para o teste. */
    UUID falhaAoEnviar(String imageBase64, String mimeType, String contexto, Throwable erro) {
        if (erro instanceof BusinessException recusada && !(erro instanceof UpstreamException)) {
            throw recusada;
        }
        log.error("Servico de media indisponivel ao enviar foto", erro);
        throw new UpstreamException("O servico de fotos esta indisponivel. Tente novamente em instantes.");
    }
}
