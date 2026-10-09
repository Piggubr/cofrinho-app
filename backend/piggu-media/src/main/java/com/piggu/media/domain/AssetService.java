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


    private final AssetRepository repositorio;
    private final StoragePort armazenamento;

    public AssetService(AssetRepository repositorio, StoragePort armazenamento) {
        this.repositorio = repositorio;
        this.armazenamento = armazenamento;
    }

    /** @param mimeType o que o cliente diz; so informativo, o tipo gravado sai dos bytes */
    @Transactional
    public Asset guardar(String imageBase64, String mimeType, String contexto, String pasta, String emailUsuario) {
        byte[] conteudo = decodificar(imageBase64);

        if (conteudo.length > TAMANHO_MAXIMO) {
            throw new BusinessException("A foto e grande demais. O limite e de 5 MB.");
        }

        // O tipo sai dos bytes, nao do que o cliente diz: um .jpg que na verdade e HTML
        // ou script nao entra.
        String tipo = tipoPeloConteudo(conteudo);
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

    /** Assinatura dos primeiros bytes: JPEG FF D8 FF, PNG 89 50 4E 47, WebP RIFF....WEBP. */
    static String tipoPeloConteudo(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        throw new BusinessException("A foto precisa ser JPEG, PNG ou WebP.");
    }

    private String extensao(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
