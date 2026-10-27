package com.piggu.identity.apple;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.web.Texto;
import com.piggu.identity.config.AppleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Confere o identity token do Entrar com Apple, como o {@code GoogleIdTokenVerifier} faz com o
 * do Google: assinatura contra o JWKS publico da Apple (em cache no decoder), emissor,
 * audiencia (Bundle ID), expiracao, nonce e e-mail.
 *
 * <p>O nonce segura um token roubado: o app gera um valor aleatorio, manda o SHA-256 dele para a
 * Apple, que o grava no token, e manda o valor original para ca. So quem gerou o nonce consegue
 * usar o token.</p>
 *
 * <p>Sem {@code piggu.apple.client-ids} a rota responde 501: o servico sobe sem a Apple.</p>
 */
@Component
public class AppleIdTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(AppleIdTokenVerifier.class);
    private static final String RECUSADO = "Login com a Apple invalido para este app.";

    private final AppleProperties propriedades;
    private final NimbusJwtDecoder decoder;

    public AppleIdTokenVerifier(AppleProperties propriedades) {
        this.propriedades = propriedades;
        // Nao busca as chaves agora: so no primeiro login.
        this.decoder = NimbusJwtDecoder.withJwkSetUri(propriedades.jwkSetUri()).build();
    }

    public boolean ligado() {
        return propriedades.ligado();
    }

    public AppleProfile verificar(String idToken, String nonce) {
        if (!ligado()) {
            throw new BusinessException("O login com a Apple ainda nao esta disponivel.",
                    HttpStatus.NOT_IMPLEMENTED, "EM_BREVE");
        }
        if (Texto.vazio(idToken) || Texto.vazio(nonce)) {
            throw new UnauthorizedException("Faca login com a Apple para continuar.");
        }

        Jwt token;
        try {
            token = decoder.decode(idToken);
        } catch (JwtException erro) {
            log.debug("Identity token da Apple recusado", erro);
            throw new UnauthorizedException("Seu login venceu ou e' invalido. Entre novamente.");
        }

        if (token.getIssuer() == null || !propriedades.issuer().equals(token.getIssuer().toString())) {
            throw new UnauthorizedException(RECUSADO);
        }
        List<String> audiencia = token.getAudience();
        if (audiencia == null || audiencia.stream().noneMatch(propriedades.clientIds()::contains)) {
            throw new UnauthorizedException(RECUSADO);
        }
        if (!sha256(nonce).equals(token.getClaimAsString("nonce"))) {
            throw new UnauthorizedException(RECUSADO);
        }
        // A Apple manda email_verified ora como texto ("true"), ora como booleano.
        if (!"true".equals(String.valueOf(token.getClaims().get("email_verified")))) {
            throw new UnauthorizedException("O e-mail da Apple nao esta verificado.");
        }
        String email = Texto.email(token.getClaimAsString("email"));
        if (email.isEmpty()) {
            throw new UnauthorizedException("A Apple nao informou o seu e-mail.");
        }
        return new AppleProfile(token.getSubject(), email);
    }

    static String sha256(String texto) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossivel) {
            throw new IllegalStateException(impossivel);
        }
    }
}
