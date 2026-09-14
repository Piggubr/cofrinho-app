package com.piggu.media.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.piggu.media.storage.DriveStorageAdapter;
import com.piggu.media.storage.LocalStorageAdapter;
import com.piggu.media.storage.StoragePort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Escolhe onde os arquivos ficam guardados.
 *
 * <p>Com credenciais do Google configuradas, usa o Drive. Sem elas, cai para a pasta
 * local e registra um aviso, para que o ambiente de desenvolvimento suba sem
 * depender de conta de servico.</p>
 */
@Configuration
@EnableConfigurationProperties(DriveProperties.class)
public class StorageConfig {

    @Bean
    public StoragePort storagePort(DriveProperties propriedades,
                                   ResourceLoader carregador,
                                   org.springframework.core.env.Environment ambiente) throws Exception {
        if (!propriedades.configurado()) {
            String pasta = ambiente.getProperty("piggu.storage.local-path", "./dados-fotos");
            return new LocalStorageAdapter(Path.of(pasta));
        }
        return new DriveStorageAdapter(construirDrive(propriedades, carregador), propriedades);
    }

    private Drive construirDrive(DriveProperties propriedades, ResourceLoader carregador) throws Exception {
        Resource credenciais = carregador.getResource(propriedades.credentialsLocation());
        if (!credenciais.exists()) {
            throw new IllegalStateException(
                    "Credenciais do Drive nao encontradas em " + propriedades.credentialsLocation());
        }

        try (InputStream fluxo = credenciais.getInputStream()) {
            GoogleCredentials conta = GoogleCredentials.fromStream(fluxo)
                    .createScoped(List.of(DriveScopes.DRIVE_FILE));

            return new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(conta))
                    .setApplicationName(propriedades.applicationName())
                    .build();
        }
    }
}
