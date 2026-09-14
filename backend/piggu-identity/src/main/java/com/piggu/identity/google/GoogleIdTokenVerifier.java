package com.piggu.identity.google;

import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.web.Texto;
import com.piggu.identity.config.GoogleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Confere o ID token do Google Sign-In.
 *
 * <p>O Apps Script fazia uma chamada HTTP ao endpoint {@code tokeninfo} a cada login e
 * guardava o resultado em cache por ate 5 minutos. Aqui a assinatura e' conferida
 * localmente contra o JWKS publico do Google, que o proprio decoder mantem em cache:
 * some a ida ate a rede no caminho critico do login e o token nao sai da maquina.</p>
 *
 * <p>As mesmas checagens do original continuam: emissor, audiencia (client ID),
 * expiracao e e-mail verificado.</p>
 */
@Component
public class GoogleIdTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifier.class);

    private final GoogleProperties propriedades;
    private final NimbusJwtDecoder decoder;

    public GoogleIdTokenVerifier(GoogleProperties propriedades) {
        if (propriedades.clientIds().isEmpty()) {
            throw new IllegalStateException(
                    "Falta configurar piggu.google.client-ids com o ID OAuth do site.");
        }
        this.propriedades = propriedades;
        this.decoder = NimbusJwtDecoder.withJwkSetUri(propriedades.jwkSetUri()).build();
    }

    public GoogleProfile verificar(String idToken) {
        if (Texto.vazio(idToken)) {
            throw new UnauthorizedException("Faca login com Google para continuar.");
        }

        Jwt token;
        try {
            token = decoder.decode(idToken);
        } catch (JwtException erro) {
            log.debug("ID token do Google recusado", erro);
            throw new UnauthorizedException("Seu login venceu ou e' invalido. Entre novamente.");
        }

        if (!propriedades.issuers().contains(token.getIssuer().toString())) {
            throw new UnauthorizedException("Login Google invalido para este site.");
        }

        List<String> audiencia = token.getAudience();
        boolean audienciaConhecida = audiencia != null
                && audiencia.stream().anyMatch(propriedades.clientIds()::contains);
        if (!audienciaConhecida) {
            throw new UnauthorizedException("Login Google invalido para este site.");
        }

        if (!Boolean.TRUE.equals(token.getClaim("email_verified"))) {
            throw new UnauthorizedException("O e-mail Google nao esta verificado.");
        }

        String email = Texto.email(token.getClaimAsString("email"));
        if (email.isEmpty()) {
            throw new UnauthorizedException("O Google nao informou o seu e-mail.");
        }

        return new GoogleProfile(
                token.getSubject(),
                email,
                Texto.limitar(token.getClaimAsString("name"), 120),
                Texto.limitar(token.getClaimAsString("given_name"), 80),
                Texto.limitar(token.getClaimAsString("picture"), 1000)
        );
    }
}
