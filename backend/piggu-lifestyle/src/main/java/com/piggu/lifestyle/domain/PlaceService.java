package com.piggu.lifestyle.domain;

import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Texto;
import com.piggu.lifestyle.api.dto.PlaceRequest;
import com.piggu.lifestyle.api.dto.PlaceResponse;
import com.piggu.lifestyle.integration.MediaClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Lugares visitados.
 *
 * <p>Porte de salvarLugar_, excluirLugar_ e carregarLugares_. As fotos ficam no
 * servico de media; aqui guardamos so o identificador.</p>
 */
@Service
public class PlaceService {

    private static final String CONTEXTO_FOTO = "LUGAR";

    private final PlaceRepository repositorio;
    private final PlaceTagService marcadores;
    private final MediaClient media;

    public PlaceService(PlaceRepository repositorio, PlaceTagService marcadores, MediaClient media) {
        this.repositorio = repositorio;
        this.marcadores = marcadores;
        this.media = media;
    }

    @Transactional(readOnly = true)
    public List<PlaceResponse> listar() {
        return repositorio.findAllByOrderByVisitDateDesc().stream().map(PlaceResponse::de).toList();
    }

    @Transactional
    public PlaceResponse criar(PlaceRequest pedido, UUID usuarioId) {
        Place lugar = new Place(
                Texto.limitar(pedido.nome(), 120),
                Texto.limitarOuPadrao(pedido.categoria(), 50, "Outros"),
                Texto.limitar(pedido.localizacao(), 200),
                pedido.nota().shortValue(),
                Texto.limitar(pedido.comentario(), 500),
                pedido.data(),
                marcadores.filtrarValidos(pedido.marcacoes()),
                pedido.valor() == null ? BigDecimal.ZERO : pedido.valor(),
                usuarioId
        );

        if (temFoto(pedido)) {
            lugar.trocarFoto(media.enviar(pedido.imageBase64(), pedido.mimeType(), CONTEXTO_FOTO));
        }

        return PlaceResponse.de(repositorio.save(lugar));
    }

    @Transactional
    public PlaceResponse atualizar(UUID id, PlaceRequest pedido, CurrentUser usuario) {
        Place lugar = buscar(id);

        if (!usuario.podeGerenciar(lugar.getUserId())) {
            throw new ForbiddenException("Voce nao pode editar este lugar.");
        }

        lugar.aplicar(
                Texto.limitar(pedido.nome(), 120),
                Texto.limitarOuPadrao(pedido.categoria(), 50, "Outros"),
                Texto.limitar(pedido.localizacao(), 200),
                pedido.nota().shortValue(),
                Texto.limitar(pedido.comentario(), 500),
                pedido.data(),
                marcadores.filtrarValidos(pedido.marcacoes()),
                pedido.valor() == null ? BigDecimal.ZERO : pedido.valor()
        );

        if (temFoto(pedido)) {
            UUID anterior = lugar.getPhotoAssetId();
            lugar.trocarFoto(media.enviar(pedido.imageBase64(), pedido.mimeType(), CONTEXTO_FOTO));
            // A foto antiga so sai depois que a nova entrou, para nao ficar sem nenhuma.
            media.apagar(anterior);
        }

        return PlaceResponse.de(repositorio.save(lugar));
    }

    @Transactional
    public void excluir(UUID id, CurrentUser usuario) {
        Place lugar = buscar(id);

        if (!usuario.podeGerenciar(lugar.getUserId())) {
            throw new ForbiddenException("Voce nao pode excluir este lugar.");
        }

        media.apagar(lugar.getPhotoAssetId());
        repositorio.delete(lugar);
    }

    private Place buscar(UUID id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new NotFoundException("Lugar nao encontrado."));
    }

    private boolean temFoto(PlaceRequest pedido) {
        return pedido.imageBase64() != null && !pedido.imageBase64().isBlank();
    }
}
