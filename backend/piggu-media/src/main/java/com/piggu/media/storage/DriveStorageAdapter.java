package com.piggu.media.storage;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.piggu.common.error.UpstreamException;
import com.piggu.media.config.DriveProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda os arquivos no Google Drive, como o Apps Script fazia.
 *
 * <p>As pastas por mes sao criadas sob demanda e ficam em cache: sem isso cada envio
 * gastaria uma consulta so para descobrir se a pasta do mes ja existe.</p>
 */
public class DriveStorageAdapter implements StoragePort {

    private static final Logger log = LoggerFactory.getLogger(DriveStorageAdapter.class);
    private static final String MIME_PASTA = "application/vnd.google-apps.folder";

    private final Drive drive;
    private final DriveProperties propriedades;
    private final Map<String, String> pastasConhecidas = new ConcurrentHashMap<>();

    public DriveStorageAdapter(Drive drive, DriveProperties propriedades) {
        this.drive = drive;
        this.propriedades = propriedades;
    }

    @Override
    public String guardar(String nome, String contentType, byte[] conteudo, String pasta) {
        try {
            File metadados = new File();
            metadados.setName(nome);
            metadados.setParents(List.of(resolverPasta(pasta)));

            File criado = drive.files()
                    .create(metadados, new ByteArrayContent(contentType, conteudo))
                    .setFields("id")
                    .execute();

            return criado.getId();
        } catch (IOException erro) {
            log.error("Falha ao enviar arquivo para o Drive", erro);
            throw new UpstreamException("Nao consegui guardar a foto agora. Tente novamente.");
        }
    }

    @Override
    public ArquivoGuardado ler(String idNoProvedor) {
        try {
            File metadados = drive.files().get(idNoProvedor).setFields("mimeType").execute();
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            drive.files().get(idNoProvedor).executeMediaAndDownloadTo(saida);
            return new ArquivoGuardado(metadados.getMimeType(), saida.toByteArray());
        } catch (IOException erro) {
            log.error("Falha ao ler arquivo {} do Drive", idNoProvedor, erro);
            throw new UpstreamException("Nao consegui carregar a foto agora.");
        }
    }

    @Override
    public void apagar(String idNoProvedor) {
        try {
            // Vai para a lixeira, nao some de vez: uma exclusao errada ainda pode ser desfeita.
            File lixeira = new File();
            lixeira.setTrashed(true);
            drive.files().update(idNoProvedor, lixeira).execute();
        } catch (IOException erro) {
            log.warn("Nao foi possivel apagar o arquivo {} no Drive", idNoProvedor, erro);
        }
    }

    private String resolverPasta(String nomeDaPasta) throws IOException {
        String raiz = propriedades.rootFolderId();
        if (nomeDaPasta == null || nomeDaPasta.isBlank()) {
            return raiz;
        }

        String emCache = pastasConhecidas.get(nomeDaPasta);
        if (emCache != null) {
            return emCache;
        }

        FileList existentes = drive.files().list()
                .setQ("name = '" + nomeDaPasta.replace("'", "") + "' and mimeType = '" + MIME_PASTA
                        + "' and '" + raiz + "' in parents and trashed = false")
                .setFields("files(id)")
                .setPageSize(1)
                .execute();

        String id = existentes.getFiles().isEmpty() ? criarPasta(nomeDaPasta, raiz)
                : existentes.getFiles().get(0).getId();
        pastasConhecidas.put(nomeDaPasta, id);
        return id;
    }

    private String criarPasta(String nome, String raiz) throws IOException {
        File pasta = new File();
        pasta.setName(nome);
        pasta.setMimeType(MIME_PASTA);
        pasta.setParents(List.of(raiz));
        return drive.files().create(pasta).setFields("id").execute().getId();
    }
}
