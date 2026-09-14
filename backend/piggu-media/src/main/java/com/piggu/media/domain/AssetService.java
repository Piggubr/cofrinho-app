package com.piggu.media.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Texto;
import com.piggu.media.storage.StoragePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.Set;
import java.util.UUID;

/**
 * Guarda, entrega e apaga imagens.
 *
 * <p>Concentra o que estava espalhado entre salvarFotoFeed_, carregarFotoFeed_,
 * excluirFotoFeed_, salvarLugar_ e carregarFotoLugar_ no Apps Script, onde cada
 * funcionalidade falava com o DriveApp por conta propria.</p>
 */
@Service
public class AssetService {

    /** Cinco megabytes, o mesmo teto que o Apps Script aplicava. */
    public static final int TAMANHO_MAXIMO = 5 * 1024 * 1024;

    private static final Set<String> TIPOS_ACEITOS = Set.of("image/jpeg", "image/png", "image/webp");
    private static final String TIPO_PADRAO = "image/jpeg";

    private final AssetRepository repositorio;
    private final StoragePort armazenamento;

    public AssetService(AssetRepository repositorio, StoragePort armazenamento) {
        this.repositorio = repositorio;
        this.armazenamento = armazenamento;
    }

    @Transactional
    public Asset guardar(String imageBase64, String mimeType, String contexto, String pasta, String emailUsuario) {
        byte[] conteudo = decodificar(imageBase64);

        if (conteudo.length > TAMANHO_MAXIMO) {
            throw new BusinessException("A foto e grande demais. O limite e de 5 MB.");
        }

        String tipo = tipoAceito(mimeType);
        String rotulo = Texto.limitarOuPadrao(contexto, 30, Asset.CONTEXTO_PADRAO);
        String nome = UUID.randomUUID() + extensao(tipo);

        String idNoProvedor = armazenamento.guardar(nome, tipo, conteudo, pasta);
        return repositorio.save(new Asset(idNoProvedor, tipo, conteudo.length, rotulo, emailUsuario));
    }

    @Transactional(readOnly = true)
    public StoragePort.ArquivoGuardado baixar(UUID id) {
        Asset asset = buscar(id);
        return armazenamento.ler(asset.getDriveFileId());
    }

    @Transactional(readOnly = true)
    public Asset detalhes(UUID id) {
        return buscar(id);
    }

    @Transactional
    public void apagar(UUID id, CurrentUser usuario) {
        Asset asset = buscar(id);
        if (!usuario.podeGerenciar(asset.getOwnerEmail())) {
            throw new ForbiddenException("Voce nao pode apagar esta foto.");
        }
        apagarInterno(asset);
    }

    /** Remocao sem checar dono, usada quando o registro que aponta para a foto ja saiu. */
    @Transactional
    public void apagarInterno(Asset asset) {
        armazenamento.apagar(asset.getDriveFileId());
        repositorio.delete(asset);
    }

    private Asset buscar(UUID id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new NotFoundException("Foto nao encontrada."));
    }

    private byte[] decodificar(String imageBase64) {
        try {
            // O front as vezes manda a data URL inteira; cortar o prefixo evita
            // uma falha confusa de decodificacao.
            String limpo = imageBase64.contains(",")
                    ? imageBase64.substring(imageBase64.indexOf(',') + 1)
                    : imageBase64;
            return Base64.getDecoder().decode(limpo.replaceAll("\\s", ""));
        } catch (IllegalArgumentException erro) {
            throw new BusinessException("A foto chegou em um formato que nao consegui ler.");
        }
    }

    private String tipoAceito(String mimeType) {
        String informado = Texto.email(mimeType);
        return TIPOS_ACEITOS.contains(informado) ? informado : TIPO_PADRAO;
    }

    private String extensao(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
