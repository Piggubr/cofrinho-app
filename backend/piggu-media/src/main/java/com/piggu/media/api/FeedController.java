package com.piggu.media.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.media.api.dto.CaptionRequest;
import com.piggu.media.api.dto.FeedPhotoRequest;
import com.piggu.media.api.dto.FeedPhotoResponse;
import com.piggu.media.domain.FeedService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Feed de fotos por mes. Substitui saveFeed, updateFeed, deleteFeed e getFeedPhoto. */
@RestController
@RequestMapping("/api/feed")
@PreAuthorize("hasAnyRole('ADMIN', 'BEATRIZ')")
public class FeedController {

    private final FeedService servico;

    public FeedController(FeedService servico) {
        this.servico = servico;
    }

    /** @param mes filtro opcional no formato AAAA-MM */
    @GetMapping
    public List<FeedPhotoResponse> listar(@RequestParam(required = false) String mes) {
        return servico.listar(mes);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeedPhotoResponse publicar(@Valid @RequestBody FeedPhotoRequest pedido,
                                      @AuthUser CurrentUser usuario) {
        usuario.exigirPremium("O mural de fotos");
        return servico.publicar(pedido, usuario.email());
    }

    @PatchMapping("/{id}/caption")
    public FeedPhotoResponse legendar(@PathVariable UUID id,
                                      @Valid @RequestBody CaptionRequest pedido,
                                      @AuthUser CurrentUser usuario) {
        return servico.legendar(id, pedido.legenda(), usuario);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthUser CurrentUser usuario) {
        servico.excluir(id, usuario);
        return ResponseEntity.noContent().build();
    }
}
