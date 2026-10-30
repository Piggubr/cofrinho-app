package com.piggu.identity.avisos;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Push no iPhone pelo APNs da Apple, com autenticacao por token (chave .p8).
 *
 * <p>A Apple aceita o mesmo JWT por ate uma hora e recusa se ele for trocado a toda hora;
 * aqui ele vale 50 minutos. Token de aparelho que a Apple diz nao existir mais (410, ou
 * 400 BadDeviceToken) e apagado.</p>
 */
@Component
public class CanalDePush {

    private static final Logger log = LoggerFactory.getLogger(CanalDePush.class);
    private static final Duration VALIDADE_DO_JWT = Duration.ofMinutes(50);

    public enum Resultado { ENVIADO, TOKEN_INVALIDO, FALHOU }

    private final AvisosProperties.Apns config;
    private final ObjectMapper json;
    private final HttpClient http;
    private final Clock relogio;

    private String jwt;
    private Instant jwtVenceEm = Instant.MIN;

    @Autowired
    public CanalDePush(AvisosProperties propriedades, ObjectMapper json) {
        this(propriedades, json, HttpClient.newBuilder().version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10)).build(), Clock.systemUTC());
    }

    CanalDePush(AvisosProperties propriedades, ObjectMapper json, HttpClient http, Clock relogio) {
        this.config = propriedades.apns();
        this.json = json;
        this.http = http;
        this.relogio = relogio;
    }

    public boolean ligado() {
        return config.ligado();
    }

    public Resultado enviar(String tokenDoAparelho, Mensagem mensagem) {
        String host = config.sandbox() ? "https://api.sandbox.push.apple.com" : "https://api.push.apple.com";
        try {
            String corpo = json.writeValueAsString(Map.of("aps", Map.of(
                    "alert", Map.of("title", mensagem.titulo(), "body", mensagem.resumo()),
                    "sound", "default")));
            HttpRequest pedido = HttpRequest.newBuilder(URI.create(host + "/3/device/" + tokenDoAparelho))
                    .timeout(Duration.ofSeconds(15))
                    .header("authorization", "bearer " + jwt())
                    .header("apns-topic", config.bundleId())
                    .header("apns-push-type", "alert")
                    .POST(HttpRequest.BodyPublishers.ofString(corpo))
                    .build();
            HttpResponse<String> resposta = http.send(pedido, HttpResponse.BodyHandlers.ofString());
            if (resposta.statusCode() == 200) {
                return Resultado.ENVIADO;
            }
            if (resposta.statusCode() == 410 || resposta.body().contains("BadDeviceToken")) {
                return Resultado.TOKEN_INVALIDO;
            }
            log.warn("APNs recusou o push: status={} corpo={}", resposta.statusCode(), resposta.body());
            return Resultado.FALHOU;
        } catch (IOException | RuntimeException | JOSEException erro) {
            log.warn("Push nao saiu: {}", erro.getMessage());
            return Resultado.FALHOU;
        } catch (InterruptedException interrompido) {
            Thread.currentThread().interrupt();
            return Resultado.FALHOU;
        }
    }

    private synchronized String jwt() throws JOSEException {
        Instant agora = relogio.instant();
        if (jwt != null && agora.isBefore(jwtVenceEm)) {
            return jwt;
        }
        SignedJWT assinado = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(config.keyId()).build(),
                new JWTClaimsSet.Builder().issuer(config.teamId()).issueTime(Date.from(agora)).build());
        assinado.sign(new ECDSASigner(chavePrivada(config.chave())));
        jwt = assinado.serialize();
        jwtVenceEm = agora.plus(VALIDADE_DO_JWT);
        return jwt;
    }

    static ECPrivateKey chavePrivada(String pem) {
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(pkcs8(pem));
        } catch (java.security.GeneralSecurityException | IllegalArgumentException erro) {
            throw new IllegalStateException("Chave do APNs invalida (esperado o conteudo do AuthKey .p8).", erro);
        }
    }

    /** PEM PKCS#8, com quebras de linha de verdade ou escritas como \n (variavel de ambiente). */
    static PKCS8EncodedKeySpec pkcs8(String pem) {
        String base64 = pem.replace("\\n", "").replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        return new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64));
    }
}
