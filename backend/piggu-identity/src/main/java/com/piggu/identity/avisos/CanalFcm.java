package com.piggu.identity.avisos;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Push no Android pelo Firebase Cloud Messaging (API HTTP v1), com a conta de servico do
 * projeto do Firebase. Sem o SDK do Firebase: e o mesmo JWT assinado que o APNs usa.
 *
 * <p>O Google troca um JWT assinado com a chave da conta por um access token de uma hora;
 * aqui ele vale 50 minutos. Token que o FCM diz nao existir mais (UNREGISTERED, ou 400
 * apontando o token) e apagado, como no iPhone. Um 404 sem UNREGISTERED (projeto errado)
 * nao apaga nada: senao uma configuracao errada esqueceria todos os aparelhos.</p>
 */
@Component
public class CanalFcm {

    private static final Logger log = LoggerFactory.getLogger(CanalFcm.class);
    private static final Duration VALIDADE_DO_ACESSO = Duration.ofMinutes(50);
    // Fixo, e nao o token_uri do JSON: a assinatura so vai para o Google.
    private static final String TROCA_DO_TOKEN = "https://oauth2.googleapis.com/token";
    private static final String ESCOPO = "https://www.googleapis.com/auth/firebase.messaging";

    private record Conta(String projeto, String email, String idDaChave, RSAPrivateKey chave) {
    }

    private final AvisosProperties.Fcm config;
    private final ObjectMapper json;
    private final HttpClient http;
    private final Clock relogio;

    private Conta conta;
    private String acesso;
    private Instant acessoVenceEm = Instant.MIN;

    @Autowired
    public CanalFcm(AvisosProperties propriedades, ObjectMapper json) {
        this(propriedades, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                Clock.systemUTC());
    }

    CanalFcm(AvisosProperties propriedades, ObjectMapper json, HttpClient http, Clock relogio) {
        this.config = propriedades.fcm();
        this.json = json;
        this.http = http;
        this.relogio = relogio;
    }

    public boolean ligado() {
        return config.ligado();
    }

    public CanalDePush.Resultado enviar(String tokenDoAparelho, Mensagem mensagem) {
        try {
            Conta dona = conta();
            String corpo = json.writeValueAsString(Map.of("message", Map.of(
                    "token", tokenDoAparelho,
                    "notification", Map.of("title", mensagem.titulo(), "body", mensagem.resumo()))));
            HttpRequest pedido = HttpRequest.newBuilder(URI.create(
                            "https://fcm.googleapis.com/v1/projects/" + dona.projeto() + "/messages:send"))
                    .timeout(Duration.ofSeconds(15))
                    .header("authorization", "Bearer " + acesso(dona))
                    .header("content-type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(corpo))
                    .build();
            HttpResponse<String> resposta = http.send(pedido, HttpResponse.BodyHandlers.ofString());
            if (resposta.statusCode() == 200) {
                return CanalDePush.Resultado.ENVIADO;
            }
            if (resposta.body().contains("UNREGISTERED")
                    || (resposta.statusCode() == 400 && resposta.body().contains("registration token"))) {
                return CanalDePush.Resultado.TOKEN_INVALIDO;
            }
            log.warn("FCM recusou o push: status={} corpo={}", resposta.statusCode(), resposta.body());
            return CanalDePush.Resultado.FALHOU;
        } catch (IOException | RuntimeException | JOSEException erro) {
            log.warn("Push no Android nao saiu: {}", erro.getMessage());
            return CanalDePush.Resultado.FALHOU;
        } catch (InterruptedException interrompido) {
            Thread.currentThread().interrupt();
            return CanalDePush.Resultado.FALHOU;
        }
    }

    private synchronized Conta conta() {
        if (conta == null) {
            JsonNode lido = json.readTree(config.contaDeServico());
            try {
                RSAPrivateKey chave = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                        .generatePrivate(CanalDePush.pkcs8(lido.path("private_key").asString()));
                conta = new Conta(lido.path("project_id").asString(), lido.path("client_email").asString(),
                        lido.path("private_key_id").asString(), chave);
            } catch (GeneralSecurityException | IllegalArgumentException erro) {
                throw new IllegalStateException(
                        "Conta de servico do FCM invalida (esperado o JSON baixado do Google Cloud).", erro);
            }
        }
        return conta;
    }

    private synchronized String acesso(Conta dona) throws JOSEException, IOException, InterruptedException {
        Instant agora = relogio.instant();
        if (acesso != null && agora.isBefore(acessoVenceEm)) {
            return acesso;
        }
        SignedJWT assinado = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(dona.idDaChave()).type(JOSEObjectType.JWT).build(),
                new JWTClaimsSet.Builder().issuer(dona.email()).audience(TROCA_DO_TOKEN).claim("scope", ESCOPO)
                        .issueTime(Date.from(agora)).expirationTime(Date.from(agora.plus(Duration.ofHours(1))))
                        .build());
        assinado.sign(new RSASSASigner(dona.chave()));
        String formulario = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer",
                StandardCharsets.UTF_8) + "&assertion=" + assinado.serialize();
        HttpResponse<String> resposta = http.send(HttpRequest.newBuilder(URI.create(TROCA_DO_TOKEN))
                        .timeout(Duration.ofSeconds(15))
                        .header("content-type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(formulario))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (resposta.statusCode() != 200) {
            throw new IllegalStateException("Google recusou a conta de servico do FCM: status="
                    + resposta.statusCode() + " corpo=" + resposta.body());
        }
        acesso = json.readTree(resposta.body()).path("access_token").asString();
        acessoVenceEm = agora.plus(VALIDADE_DO_ACESSO);
        return acesso;
    }
}
