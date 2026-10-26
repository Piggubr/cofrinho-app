package com.piggu.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuracao do Entrar com Apple.
 *
 * @param clientIds audiencias aceitas: o Bundle ID do app ({@code br.com.piggu.app}) e, se um
 *                  dia o site tiver o botao, o Services ID. Vazio desliga a rota (501).
 * @param jwkSetUri chaves publicas da Apple usadas para conferir a assinatura do token
 * @param issuer    emissor aceito
 */
@ConfigurationProperties(prefix = "piggu.apple")
public record AppleProperties(
        List<String> clientIds,
        String jwkSetUri,
        String issuer
) {

    public AppleProperties {
        clientIds = clientIds == null ? List.of() : clientIds.stream().map(String::trim).filter(id -> !id.isEmpty()).toList();
        jwkSetUri = (jwkSetUri == null || jwkSetUri.isBlank()) ? "https://appleid.apple.com/auth/keys" : jwkSetUri;
        issuer = (issuer == null || issuer.isBlank()) ? "https://appleid.apple.com" : issuer;
    }

    public boolean ligado() {
        return !clientIds.isEmpty();
    }
}
