package com.piggu.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Chaves e prazos dos tokens emitidos pelo Piggu.
 *
 * @param issuer     identificador do emissor, gravado na claim {@code iss}
 * @param privateKey chave RSA privada em PEM (PKCS#8), usada para assinar
 * @param publicKey  chave RSA publica em PEM (X.509), publicada no JWKS
 * @param accessTtl  validade do token de acesso
 * @param refreshTtl validade da sessao longa — 30 dias, como no Apps Script
 */
@ConfigurationProperties(prefix = "piggu.jwt")
public record JwtProperties(
        String issuer,
        String privateKey,
        String publicKey,
        Duration accessTtl,
        Duration refreshTtl
) {

    public JwtProperties {
        issuer = (issuer == null || issuer.isBlank()) ? "https://piggu.app" : issuer;
        accessTtl = accessTtl == null ? Duration.ofMinutes(30) : accessTtl;
        refreshTtl = refreshTtl == null ? Duration.ofDays(30) : refreshTtl;
    }
}
