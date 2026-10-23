package com.piggu.identity.google;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.identity.config.GoogleProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

/** O ID token do Google conferido contra um JWKS local: assinatura, emissor, audiencia e e-mail. */
class GoogleIdTokenVerifierTest {

    private static final String SITE = "site.apps.googleusercontent.com";

    private static HttpServer jwks;
    private static RSAKey chave;
    private static GoogleIdTokenVerifier verificador;

    @BeforeAll
    static void subirJwks() throws Exception {
        chave = new RSAKeyGenerator(2048).keyID("k1").generate();
        byte[] corpo = new JWKSet(chave.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        jwks = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        jwks.createContext("/certs", troca -> {
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(200, corpo.length);
            try (OutputStream saida = troca.getResponseBody()) {
                saida.write(corpo);
            }
        });
        jwks.start();
        String uri = "http://127.0.0.1:" + jwks.getAddress().getPort() + "/certs";
        verificador = new GoogleIdTokenVerifier(new GoogleProperties(List.of(SITE), uri, null));
    }

    @AfterAll
    static void derrubarJwks() {
        jwks.stop(0);
    }

    @Test
    @DisplayName("token valido vira o perfil, com o e-mail normalizado")
    void tokenValido() throws Exception {
        GoogleProfile perfil = verificador.verificar(token(c -> { }));

        assertThat(perfil).isEqualTo(new GoogleProfile("sub-1", "ana@gmail.com", "Ana Souza", "Ana",
                "https://foto.test/ana.jpg"));
    }

    @Test
    @DisplayName("token vazio, ilegivel ou vencido e recusado")
    void tokenInvalido() throws Exception {
        String vencido = token(c -> c.expirationTime(Date.from(Instant.now().minusSeconds(3600))));

        assertThatThrownBy(() -> verificador.verificar(" ")).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> verificador.verificar("nao.e.jwt")).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> verificador.verificar(vencido))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("venceu");
    }

    @Test
    @DisplayName("emissor ou audiencia de outro site e recusado")
    void outroSite() throws Exception {
        String outroEmissor = token(c -> c.issuer("https://evil.test"));
        String outraAudiencia = token(c -> c.audience("outro.apps.googleusercontent.com"));

        assertThatThrownBy(() -> verificador.verificar(outroEmissor))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("invalido para este site");
        assertThatThrownBy(() -> verificador.verificar(outraAudiencia))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("invalido para este site");
    }

    @Test
    @DisplayName("e-mail nao verificado ou ausente e recusado")
    void emailRecusado() throws Exception {
        String naoVerificado = token(c -> c.claim("email_verified", false));
        String semEmail = token(c -> c.claim("email", null));

        assertThatThrownBy(() -> verificador.verificar(naoVerificado))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("nao esta verificado");
        assertThatThrownBy(() -> verificador.verificar(semEmail))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("nao informou");
    }

    @Test
    @DisplayName("sem client ID configurado o servico nem sobe")
    void semClientId() {
        GoogleProperties semId = new GoogleProperties(List.of(" "), null, null);

        assertThatThrownBy(() -> new GoogleIdTokenVerifier(semId)).isInstanceOf(IllegalStateException.class);
    }

    private static String token(Consumer<JWTClaimsSet.Builder> ajuste) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(SITE)
                .subject("sub-1")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .claim("email", " Ana@Gmail.com ")
                .claim("email_verified", true)
                .claim("name", "Ana Souza")
                .claim("given_name", "Ana")
                .claim("picture", "https://foto.test/ana.jpg");
        ajuste.accept(claims);
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").type(JOSEObjectType.JWT).build(),
                claims.build());
        jwt.sign(new RSASSASigner(chave));
        return jwt.serialize();
    }
}
