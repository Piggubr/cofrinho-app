package com.piggu.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuracao do login Google.
 *
 * @param clientIds IDs OAuth aceitos. O Apps Script lia GOOGLE_CLIENT_ID e
 *                  GOOGLE_CLIENT_ID_TEST das propriedades do script para permitir
 *                  uma implantacao de testes em paralelo; aqui e' uma lista.
 * @param jwkSetUri chaves publicas do Google usadas para conferir a assinatura do token
 * @param issuers   emissores aceitos
 */
@ConfigurationProperties(prefix = "piggu.google")
public record GoogleProperties(
        List<String> clientIds,
        String jwkSetUri,
        List<String> issuers
) {

    public GoogleProperties {
        clientIds = clientIds == null ? List.of() : clientIds.stream().map(String::trim).filter(id -> !id.isEmpty()).toList();
        jwkSetUri = (jwkSetUri == null || jwkSetUri.isBlank()) ? "https://www.googleapis.com/oauth2/v3/certs" : jwkSetUri;
        issuers = (issuers == null || issuers.isEmpty())
                ? List.of("accounts.google.com", "https://accounts.google.com")
                : issuers;
    }
}
