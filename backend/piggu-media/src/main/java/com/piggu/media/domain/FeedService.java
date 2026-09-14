package com.piggu.media.domain;

import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Texto;
import com.piggu.media.api.dto.FeedPhotoRequest;
import com.piggu.media.api.dto.FeedPhotoResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.List;
import java.util.UUID;

/**
 * Feed de fotos por mes.
 *
 * <p>Porte de salvarFotoFeed_, atualizarFotoFeed_, excluirFotoFeed_ e
 * obterPastaFeedMes_.</p>
 */
@Service
public class FeedService {

    private static final String CONTEXTO = "FEED";
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final FeedPhotoRepository repositorio;
    private final AssetService assets;

    public FeedService(FeedPhotoRepository repositorio, AssetService assets) {
        this.repositorio = repositorio;
        this.assets = assets;
    }

    @Transactional(readOnly = true)
    public List<FeedPhotoResponse> listar(String mesKey) {
        List<FeedPhoto> fotos = Texto.vazio(mesKey)
                ? repositorio.findAllByOrderByMonthKeyDescCreatedAtDesc()
                : repositorio.findByMonthKeyOrderByCreatedAtDesc(mesKey);
        return fotos.stream().map(FeedPhotoResponse::de).toList();
    }

    @Transactional
    public FeedPhotoResponse publicar(FeedPhotoRequest pedido, String emailUsuario) {
        Asset asset = assets.guardar(
                pedido.imageBase64(),
                pedido.mimeType(),
                CONTEXTO,
                nomeDaPasta(pedido.mesKey()),
                emailUsuario
        );
        FeedPhoto foto = new FeedPhoto(pedido.mesKey(), asset.getId(), emailUsuario);
        return FeedPhotoResponse.de(repositorio.save(foto));
    }

    @Transactional
    public FeedPhotoResponse legendar(UUID id, String legenda, CurrentUser usuario) {
        FeedPhoto foto = buscar(id);
        if (!usuario.podeGerenciar(foto.getUserEmail())) {
            throw new ForbiddenException("Voce nao pode editar esta foto.");
        }
        foto.legendar(Texto.limitar(legenda, 300));
        return FeedPhotoResponse.de(repositorio.save(foto));
    }

    @Transactional
    public void excluir(UUID id, CurrentUser usuario) {
        FeedPhoto foto = buscar(id);
        if (!usuario.podeGerenciar(foto.getUserEmail())) {
            throw new ForbiddenException("Voce nao pode apagar esta foto.");
        }
        Asset asset = assets.detalhes(foto.getAssetId());
        repositorio.delete(foto);
        assets.apagarInterno(asset);
    }

    private FeedPhoto buscar(UUID id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new NotFoundException("Foto nao encontrada."));
    }

    /** Pasta no formato "2026-09 - Setembro", igual a que o Apps Script criava. */
    private String nomeDaPasta(String mesKey) {
        int mes = Integer.parseInt(mesKey.substring(5));
        String nome = Month.of(mes).getDisplayName(TextStyle.FULL_STANDALONE, PT_BR);
        return mesKey + " - " + nome.substring(0, 1).toUpperCase(PT_BR) + nome.substring(1);
    }
}
