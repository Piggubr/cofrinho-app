package com.piggu.identity.apple;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.piggu.identity.avisos.CanalDePush;
import com.piggu.identity.config.AppleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Revogar o Entrar com Apple quando a conta e excluida, como a Apple exige (Diretriz 5.1.1(v)).
 *
 * <p>No login o app manda o authorization code; aqui ele vira um refresh token
 * ({@code /auth/token}), guardado na conta. Ao excluir, o token vai para {@code /auth/revoke}.
 * Os dois pedidos levam um client secret: um JWT ES256 assinado com a chave .p8.</p>
 *
 * <p>Falha da Apple nunca impede o login nem a exclusao: fica no log.</p>
 */
@Component
public class RevogacaoDaApple {

    private static final Logger log = LoggerFactory.getLogger(RevogacaoDaApple.class);
    private static final String APPLE = "https://appleid.apple.com";

    private final AppleProperties config;
    private final ObjectMapper json;
    private final HttpClient http;
    private final Clock relogio;

    @Autowired
    public RevogacaoDaApple(AppleProperties config, ObjectMapper json) {
        this(config, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), Clock.systemUTC());
    }

    RevogacaoDaApple(AppleProperties config, ObjectMapper json, HttpClient http, Clock relogio) {
        this.config = config;
        this.json = json;
        this.http = http;
        this.relogio = relogio;
    }

    public boolean ligada() {
        return config.revogacaoLigada();
    }

    /** O refresh token do codigo, ou nulo se a Apple recusar ou estiver fora do ar. */
    public String trocarCodigo(String codigo, String clienteId) {
        HttpResponse<String> resposta = postar("/auth/token", clienteId,
                Map.of("grant_type", "authorization_code", "code", codigo));
        if (resposta == null) {
            return null;
        }
        if (resposta.statusCode() != 200) {
            log.warn("Apple recusou o codigo: status={} corpo={}", resposta.statusCode(), resposta.body());
            return null;
        }
        try {
            String token = json.readTree(resposta.body()).path("refresh_token").asString();
            return token.isEmpty() ? null : token;
        } catch (RuntimeException ilegivel) {
            log.warn("Resposta da Apple ilegivel ao trocar o codigo: {}", ilegivel.getMessage());
            return null;
        }
    }

    public boolean revogar(String refreshToken, String clienteId) {
        HttpResponse<String> resposta = postar("/auth/revoke", clienteId,
                Map.of("token", refreshToken, "token_type_hint", "refresh_token"));
        if (resposta == null) {
            return false;
        }
        int status = resposta.statusCode();
        if (status != 200) {
            log.warn("Apple recusou a revogacao: status={} corpo={}", status, resposta.body());
        }
        return status == 200;
    }

    private HttpResponse<String> postar(String caminho, String clienteId, Map<String, String> campos) {
        try {
            Map<String, String> corpo = new LinkedHashMap<>(campos);
            corpo.put("client_id", clienteId);
            corpo.put("client_secret", clientSecret(clienteId));
            HttpRequest pedido = HttpRequest.newBuilder(URI.create(APPLE + caminho))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formulario(corpo)))
                    .build();
            return http.send(pedido, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | RuntimeException | JOSEException erro) {
            log.warn("Pedido a Apple ({}) nao saiu: {}", caminho, erro.getMessage());
            return null;
        } catch (InterruptedException interrompido) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    String clientSecret(String clienteId) throws JOSEException {
        Instant agora = relogio.instant();
        SignedJWT assinado = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(config.keyId()).build(),
                new JWTClaimsSet.Builder()
                        .issuer(config.teamId())
                        .subject(clienteId)
                        .audience(APPLE)
                        .issueTime(Date.from(agora))
                        .expirationTime(Date.from(agora.plus(Duration.ofMinutes(5))))
                        .build());
        assinado.sign(new ECDSASigner(CanalDePush.chavePrivada(config.chave())));
        return assinado.serialize();
    }

    private static String formulario(Map<String, String> campos) {
        return campos.entrySet().stream()
                .map(campo -> campo.getKey() + "=" + URLEncoder.encode(campo.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
