package com.piggu.finance.integration;

import com.piggu.common.error.BusinessException;
import com.piggu.common.web.CorrelacaoFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Quem e da familia segundo o identity ({@code GET /api/family}), com o token de quem pediu.
 *
 * <p>So e chamado quando o {@code dividirCom} traz alguem que o finance ainda nao viu (o
 * parceiro que acabou de entrar e nunca abriu o app). O caminho comum nao chama ninguem (D-192).
 * Prazo curto: com o identity fora, a divisao espera no maximo 2 s para conectar e 5 s para
 * a resposta, e vira 503.</p>
 */
@Component
public class FamiliaNoIdentity {

    static final String FORA_DO_AR = "Essa parte do Piggu está fora do ar agora. Tente de novo em instantes.";
    private static final Logger log = LoggerFactory.getLogger(FamiliaNoIdentity.class);

    private final RestClient cliente;

    @Autowired
    public FamiliaNoIdentity(RestClient.Builder builder, @Value("${piggu.servicos.identity-url}") String baseUrl) {
        this(builder.baseUrl(baseUrl).requestFactory(prazos()).build());
    }

    FamiliaNoIdentity(RestClient cliente) {
        this.cliente = cliente;
    }

    private static SimpleClientHttpRequestFactory prazos() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(2));
        fabrica.setReadTimeout(Duration.ofSeconds(5));
        return fabrica;
    }

    /** Ids das pessoas da familia de quem fez a requisicao; identity fora ou recusando vira 503. */
    public Set<UUID> membros() {
        try {
            JsonNode familia = cliente.get().uri("/api/family")
                    .header(HttpHeaders.AUTHORIZATION, autorizacao())
                    .headers(cabecalhos -> {
                        String id = MDC.get(CorrelacaoFilter.MDC_REQUISICAO);
                        if (id != null) {
                            cabecalhos.set(CorrelacaoFilter.CABECALHO, id);
                        }
                    })
                    .retrieve()
                    .body(JsonNode.class);
            Set<UUID> ids = new HashSet<>();
            if (familia != null) {
                familia.path("membros").forEach(membro -> ids.add(UUID.fromString(membro.path("id").asString())));
            }
            return ids;
        } catch (RestClientException | IllegalArgumentException falha) {
            log.warn("Nao consegui confirmar a familia no identity: {}", falha.toString());
            throw new BusinessException(FORA_DO_AR, HttpStatus.SERVICE_UNAVAILABLE, "SERVICO_INDISPONIVEL");
        }
    }

    private static String autorizacao() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof Jwt jwt) {
            return "Bearer " + jwt.getTokenValue();
        }
        throw new BusinessException(FORA_DO_AR, HttpStatus.SERVICE_UNAVAILABLE, "SERVICO_INDISPONIVEL");
    }
}
