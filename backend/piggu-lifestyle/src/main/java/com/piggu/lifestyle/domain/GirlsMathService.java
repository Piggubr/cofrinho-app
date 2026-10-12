package com.piggu.lifestyle.domain;

import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.common.web.Meses;
import com.piggu.common.web.Texto;
import com.piggu.lifestyle.api.dto.GirlsMathRequest;
import com.piggu.lifestyle.api.dto.GirlsMathResponse;
import com.piggu.lifestyle.integration.MediaClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Girls Math: o que a pessoa decidiu nao comprar, e quanto deixou de gastar com isso.
 *
 * <p>Privado como as metas do mes: item de outra pessoa responde como inexistente. A foto
 * fica no servico de media; aqui guardamos so o identificador.</p>
 */
@Service
public class GirlsMathService {

    private static final String CONTEXTO_FOTO = "GIRLS_MATH";

    private final GirlsMathItemRepository repositorio;
    private final MediaClient media;

    public GirlsMathService(GirlsMathItemRepository repositorio, MediaClient media) {
        this.repositorio = repositorio;
        this.media = media;
    }

    /** Do mais recente para o mais antigo. */
    @Transactional(readOnly = true)
    public List<GirlsMathResponse> listar(UUID usuarioId) {
        return repositorio.findByUserIdOrderByItemDateDescCreatedAtDesc(usuarioId).stream()
                .map(GirlsMathResponse::de)
                .toList();
    }

    @Transactional
    public GirlsMathResponse criar(GirlsMathRequest pedido, CurrentUser usuario) {
        GirlsMathItem item = new GirlsMathItem(
                Texto.limitar(pedido.produto(), 120),
                Texto.limitar(pedido.loja(), 120),
                pedido.valor(),
                pedido.data() == null ? LocalDate.now(Meses.BRASILIA) : pedido.data(),
                usuario.id()
        );

        if (temFoto(pedido)) {
            // O piggu-media nao aceita envio de membro (ver AssetController); melhor avisar
            // aqui do que devolver a falha do media como servico indisponivel.
            if (usuario.role() == PigguRole.MEMBRO) {
                throw new ForbiddenException("Foto no Girls Math e so para titular e parceiro.");
            }
            item.trocarFoto(media.enviar(pedido.imageBase64(), pedido.mimeType(), CONTEXTO_FOTO));
        }

        return GirlsMathResponse.de(repositorio.save(item));
    }

    @Transactional
    public void excluir(UUID id, UUID usuarioId) {
        GirlsMathItem item = repositorio.findByIdAndUserId(id, usuarioId)
                .orElseThrow(() -> new NotFoundException("Item nao encontrado."));
        media.apagar(item.getPhotoAssetId());
        repositorio.delete(item);
    }

    private boolean temFoto(GirlsMathRequest pedido) {
        return pedido.imageBase64() != null && !pedido.imageBase64().isBlank();
    }
}
