package com.piggu.identity.apple;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.identity.config.AppleProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O identity token da Apple conferido contra um JWKS local: assinatura, emissor, audiencia, nonce e e-mail. */
class AppleIdTokenVerifierTest {

    private static final String APP = "br.com.piggu.app";
    private static final String NONCE = "nonce-cru-do-app";

    private static HttpServer jwks;
    private static RSAKey chave;
    private static AppleIdTokenVerifier verificador;

    @BeforeAll
    static void subirJwks() throws Exception {
        chave = new RSAKeyGenerator(2048).keyID("apple-1").generate();
        byte[] corpo = new JWKSet(chave.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        jwks = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        jwks.createContext("/auth/keys", troca -> {
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(200, corpo.length);
            try (OutputStream saida = troca.getResponseBody()) {
                saida.write(corpo);
            }
        });
        jwks.start();
        String uri = "http://127.0.0.1:" + jwks.getAddress().getPort() + "/auth/keys";
        verificador = new AppleIdTokenVerifier(new AppleProperties(List.of(APP), uri, null, null, null, null));
    }

    @AfterAll
    static void derrubarJwks() {
        jwks.stop(0);
    }

    @Test
    @DisplayName("token valido vira o perfil, com o e-mail normalizado; email_verified vale como texto ou booleano")
    void tokenValido() throws Exception {
        assertThat(verificador.verificar(token(c -> { }), NONCE))
                .isEqualTo(new AppleProfile("001234.apple", "ana@privaterelay.appleid.com", APP));
        assertThat(verificador.verificar(token(c -> c.claim("email_verified", true)), NONCE).email())
                .isEqualTo("ana@privaterelay.appleid.com");
    }

    @Test
    @DisplayName("o nonce do token tem que ser o SHA-256 do nonce que o app manda")
    void nonce() throws Exception {
        String token = token(c -> { });

        assertThatThrownBy(() -> verificador.verificar(token, "outro")).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> verificador.verificar(token, " ")).isInstanceOf(UnauthorizedException.class);
        // Quem copiou o hash do token nao passa: o servidor faz o hash de novo.
        assertThatThrownBy(() -> verificador.verificar(token, AppleIdTokenVerifier.sha256(NONCE)))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> verificador.verificar(token(c -> c.claim("nonce", null)), NONCE))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("token vazio, ilegivel, vencido ou assinado por outra chave e recusado")
    void tokenInvalido() throws Exception {
        String vencido = token(c -> c.expirationTime(Date.from(Instant.now().minusSeconds(3600))));
        RSAKey outraChave = new RSAKeyGenerator(2048).keyID("apple-1").generate();
        String falsificado = assinar(claims(c -> { }), outraChave);

        assertThatThrownBy(() -> verificador.verificar(" ", NONCE)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> verificador.verificar("nao.e.jwt", NONCE)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> verificador.verificar(vencido, NONCE)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> verificador.verificar(falsificado, NONCE)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("emissor ou audiencia de outro app e recusado")
    void outroApp() throws Exception {
        String outroEmissor = token(c -> c.issuer("https://accounts.google.com"));
        String outraAudiencia = token(c -> c.audience("com.outro.app"));

        assertThatThrownBy(() -> verificador.verificar(outroEmissor, NONCE))
                .isInstanceOf(UnauthorizedException.class).hasMessageContaining("invalido para este app");
        assertThatThrownBy(() -> verificador.verificar(outraAudiencia, NONCE))
                .isInstanceOf(UnauthorizedException.class).hasMessageContaining("invalido para este app");
    }

    @Test
    @DisplayName("e-mail nao verificado ou ausente e recusado")
    void emailRecusado() throws Exception {
        String naoVerificado = token(c -> c.claim("email_verified", "false"));
        String semEmail = token(c -> c.claim("email", null));

        assertThatThrownBy(() -> verificador.verificar(naoVerificado, NONCE))
                .isInstanceOf(UnauthorizedException.class).hasMessageContaining("nao esta verificado");
        assertThatThrownBy(() -> verificador.verificar(semEmail, NONCE))
                .isInstanceOf(UnauthorizedException.class).hasMessageContaining("nao informou");
    }

    @Test
    @DisplayName("sem client ID configurado o servico sobe, mas a rota responde 501")
    void desligado() throws Exception {
        AppleIdTokenVerifier desligado = new AppleIdTokenVerifier(new AppleProperties(List.of(" "), null, null, null, null, null));
        String token = token(c -> { });

        assertThat(desligado.ligado()).isFalse();
        assertThatThrownBy(() -> desligado.verificar(token, NONCE))
                .isInstanceOfSatisfying(BusinessException.class, erro -> {
                    assertThat(erro.getStatus()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
                    assertThat(erro.getCodigo()).isEqualTo("EM_BREVE");
                });
    }

    private static String token(Consumer<JWTClaimsSet.Builder> ajuste) throws Exception {
        return assinar(claims(ajuste), chave);
    }

    private static JWTClaimsSet claims(Consumer<JWTClaimsSet.Builder> ajuste) {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer("https://appleid.apple.com")
                .audience(APP)
                .subject("001234.apple")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .claim("nonce", AppleIdTokenVerifier.sha256(NONCE))
                .claim("email", " Ana@PrivateRelay.AppleId.com ")
                .claim("email_verified", "true")
                .claim("is_private_email", "true");
        ajuste.accept(claims);
        return claims.build();
    }

    private static String assinar(JWTClaimsSet claims, RSAKey comChave) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(comChave.getKeyID()).type(JOSEObjectType.JWT).build(), claims);
        jwt.sign(new RSASSASigner(comChave));
        return jwt.serialize();
    }
}
