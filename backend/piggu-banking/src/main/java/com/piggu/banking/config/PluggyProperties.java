package com.piggu.banking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciais do agregador Open Finance Pluggy.
 *
 * <p>Vem so de variaveis de ambiente (PLUGGY_CLIENT_ID, PLUGGY_CLIENT_SECRET). Sem
 * elas o servico sobe normalmente e as rotas de banco respondem com aviso claro.</p>
 *
 * @param sandbox mostra os bancos de teste da Pluggy no widget; so para desenvolvimento
 */
@ConfigurationProperties(prefix = "piggu.pluggy")
public record PluggyProperties(String baseUrl, String clientId, String clientSecret, boolean sandbox) {

    public PluggyProperties {
        baseUrl = vazio(baseUrl) ? "https://api.pluggy.ai" : baseUrl;
    }

    public boolean habilitado() {
        return !vazio(clientId) && !vazio(clientSecret);
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
