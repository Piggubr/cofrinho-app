package com.piggu.identity.billing;

import com.google.api.services.androidpublisher.model.ExternalAccountIdentifiers;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseLineItem;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * O Google dos testes: a Play Developer API em memoria (assinaturas por token) e o Google que
 * assina o token OIDC do push do Pub/Sub, com as chaves publicas num JWKS local.
 */
final class GoogleDeTeste implements GooglePlayApi {

    static final String AUDIENCIA = "https://api.piggu.test/api/billing/webhooks/google-play";
    static final String CONTA_DO_PUSH = "pubsub-push@piggu-teste.iam.gserviceaccount.com";

    final Map<String, SubscriptionPurchaseV2> assinaturas = new ConcurrentHashMap<>();
    final List<String> confirmadas = new CopyOnWriteArrayList<>();
    volatile boolean confirmacaoFalha;

    private final RSAKey chave;
    private final String jwks;

    GoogleDeTeste() {
        try {
            chave = new RSAKeyGenerator(2048).keyID("google-teste").generate();
            byte[] corpo = new JWKSet(chave.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            HttpServer servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            servidor.createContext("/certs", troca -> {
                troca.getResponseHeaders().add("Content-Type", "application/json");
                troca.sendResponseHeaders(200, corpo.length);
                try (OutputStream saida = troca.getResponseBody()) {
                    saida.write(corpo);
                }
            });
            servidor.start();
            jwks = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/certs";
        } catch (IOException falha) {
            throw new UncheckedIOException(falha);
        } catch (JOSEException falha) {
            throw new IllegalStateException(falha);
        }
    }

    String jwks() {
        return jwks;
    }

    void limpar() {
        assinaturas.clear();
        confirmadas.clear();
        confirmacaoFalha = false;
    }

    /** Assinatura como o subscriptionsv2.get devolve, ainda sem acknowledge. */
    SubscriptionPurchaseV2 assinatura(String token, UUID conta, String produto, String estado, Instant vence) {
        SubscriptionPurchaseV2 compra = new SubscriptionPurchaseV2()
                .setSubscriptionState(estado)
                .setAcknowledgementState("ACKNOWLEDGEMENT_STATE_PENDING")
                .setStartTime(Instant.now().minusSeconds(60).toString())
                .setLineItems(List.of(new SubscriptionPurchaseLineItem().setProductId(produto).setExpiryTime(vence.toString())));
        if (conta != null) {
            compra.setExternalAccountIdentifiers(new ExternalAccountIdentifiers().setObfuscatedExternalAccountId(conta.toString()));
        }
        assinaturas.put(token, compra);
        return compra;
    }

    /** Corpo do push do Pub/Sub, com o aviso do Google Play em base64 e os campos que o Pub/Sub repete. */
    static String push(Map<String, Object> aviso) {
        String dados = Base64.getEncoder().encodeToString(JsonMapper.builder().build().writeValueAsBytes(aviso));
        return JsonMapper.builder().build().writeValueAsString(Map.of(
                "message", Map.of("data", dados, "messageId", "1", "message_id", "1",
                        "publishTime", "2026-10-28T12:00:00Z", "publish_time", "2026-10-28T12:00:00Z",
                        "attributes", Map.of()),
                "subscription", "projects/piggu/subscriptions/play-push"));
    }

    static Map<String, Object> avisoDeAssinatura(int tipo, String token) {
        return Map.of("version", "1.0", "packageName", "br.com.piggu.app", "eventTimeMillis", "1",
                "subscriptionNotification", Map.of("version", "1.0", "notificationType", tipo, "purchaseToken", token));
    }

    /** Token OIDC do push, assinado por esta chave. */
    String token(String audiencia, String email) {
        return token(chave, audiencia, email);
    }

    static String token(RSAKey chave, String audiencia, String email) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(audiencia)
                .subject("1234567890")
                .claim("email", email)
                .claim("email_verified", true)
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(chave.getKeyID())
                    .type(JOSEObjectType.JWT).build(), claims);
            jwt.sign(new RSASSASigner(chave));
            return jwt.serialize();
        } catch (JOSEException falha) {
            throw new IllegalStateException(falha);
        }
    }

    @Override
    public boolean configurada() {
        return true;
    }

    @Override
    public Optional<SubscriptionPurchaseV2> assinatura(String token) {
        return Optional.ofNullable(assinaturas.get(token));
    }

    @Override
    public void confirmar(String produto, String token) throws IOException {
        if (confirmacaoFalha) {
            throw new IOException("Google fora do ar");
        }
        confirmadas.add(token);
        assinaturas.get(token).setAcknowledgementState("ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED");
    }
}
