package com.piggu.identity.api;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Publica a chave publica de assinatura.
 *
 * <p>E' por aqui que finance, rewards, lifestyle e media aprendem a validar os tokens
 * do Piggu sem nenhuma chamada extra por requisicao.</p>
 */
@RestController
public class JwksController {

    private final JWKSource<SecurityContext> fonteDeChaves;

    public JwksController(JWKSource<SecurityContext> fonteDeChaves) {
        this.fonteDeChaves = fonteDeChaves;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> chaves() throws Exception {
        JWKSet conjunto = new JWKSet(fonteDeChaves.get(new JWKSelector(new JWKMatcher.Builder().build()), null));
        return conjunto.toJSONObject();
    }
}
