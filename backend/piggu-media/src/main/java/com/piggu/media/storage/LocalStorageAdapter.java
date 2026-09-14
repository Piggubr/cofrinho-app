package com.piggu.media.storage;

import com.piggu.common.error.UpstreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Guarda os arquivos em uma pasta do proprio servidor.
 *
 * <p>Existe para o ambiente local: sem ela seria preciso uma conta de servico do
 * Google so para abrir o projeto e testar uma tela. Em producao o adaptador ativo
 * e o {@link DriveStorageAdapter}.</p>
 */
public class LocalStorageAdapter implements StoragePort {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageAdapter.class);

    private final Path raiz;

    public LocalStorageAdapter(Path raiz) {
        this.raiz = raiz;
        try {
            Files.createDirectories(raiz);
            log.warn("Google Drive nao configurado: as fotos serao gravadas em {}", raiz.toAbsolutePath());
        } catch (IOException erro) {
            throw new IllegalStateException("Nao foi possivel criar a pasta de arquivos em " + raiz, erro);
        }
    }

    @Override
    public String guardar(String nome, String contentType, byte[] conteudo, String pasta) {
        try {
            Path destino = diretorio(pasta);
            String identificador = (pasta == null || pasta.isBlank() ? "" : pasta + "/")
                    + UUID.randomUUID() + extensao(contentType);
            Files.write(raiz.resolve(identificador), conteudo);
            return identificador;
        } catch (IOException erro) {
            log.error("Falha ao gravar arquivo local", erro);
            throw new UpstreamException("Nao consegui guardar a foto agora.");
        }
    }

    @Override
    public ArquivoGuardado ler(String idNoProvedor) {
        try {
            Path arquivo = resolverSeguro(idNoProvedor);
            String tipo = Files.probeContentType(arquivo);
            return new ArquivoGuardado(tipo == null ? "image/jpeg" : tipo, Files.readAllBytes(arquivo));
        } catch (IOException erro) {
            log.error("Falha ao ler arquivo local {}", idNoProvedor, erro);
            throw new UpstreamException("Nao consegui carregar a foto agora.");
        }
    }

    @Override
    public void apagar(String idNoProvedor) {
        try {
            Files.deleteIfExists(resolverSeguro(idNoProvedor));
        } catch (IOException erro) {
            log.warn("Nao foi possivel apagar o arquivo local {}", idNoProvedor, erro);
        }
    }

    private Path diretorio(String pasta) throws IOException {
        Path destino = pasta == null || pasta.isBlank() ? raiz : raiz.resolve(pasta);
        Files.createDirectories(destino);
        return destino;
    }

    /**
     * Impede que um identificador com .. escape da pasta de arquivos.
     *
     * <p>O identificador vem do banco, mas normalizar aqui custa nada e fecha a porta
     * para qualquer caminho inesperado.</p>
     */
    private Path resolverSeguro(String identificador) throws IOException {
        Path alvo = raiz.resolve(identificador).normalize();
        if (!alvo.startsWith(raiz.normalize())) {
            throw new IOException("Caminho de arquivo invalido: " + identificador);
        }
        return alvo;
    }

    private String extensao(String contentType) {
        return switch (contentType == null ? "" : contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
