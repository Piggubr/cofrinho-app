package com.piggu.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Acesso ao Google Drive.
 *
 * @param credentialsLocation caminho das credenciais da conta de servico, no formato
 *                            classpath: ou file:. Vazio deixa o servico subir em modo
 *                            de armazenamento local, util para desenvolvimento.
 * @param rootFolderId        pasta raiz onde as imagens do Piggu sao criadas
 * @param applicationName     nome enviado nas chamadas a API
 */
@ConfigurationProperties(prefix = "piggu.drive")
public record DriveProperties(String credentialsLocation, String rootFolderId, String applicationName) {

    public DriveProperties {
        applicationName = (applicationName == null || applicationName.isBlank())
                ? "Piggu Media"
                : applicationName;
    }

    public boolean configurado() {
        return credentialsLocation != null && !credentialsLocation.isBlank();
    }
}
