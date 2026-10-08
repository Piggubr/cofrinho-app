package com.piggu.media.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.media.api.dto.AssetResponse;
import com.piggu.media.api.dto.AssetUploadRequest;
import com.piggu.media.domain.AssetService;
import com.piggu.media.storage.StoragePort;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * Arquivos de imagem.
 *
 * <p>A diferenca mais visivel em relacao ao Apps Script: as imagens sao entregues
 * como bytes, com tipo e cache, em vez de uma data URL em base64 dentro de um JSON.
 * Base64 inflava cada foto em um terco e impedia o navegador de guardar em cache.</p>
 */
@RestController
@RequestMapping("/api/assets")
@PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
public class AssetController {

    private final AssetService servico;

    public AssetController(AssetService servico) {
        this.servico = servico;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssetResponse enviar(@Valid @RequestBody AssetUploadRequest pedido,
                                @AuthUser CurrentUser usuario) {
        return AssetResponse.de(servico.guardar(
                pedido.imageBase64(), pedido.mimeType(), pedido.contexto(), null, usuario.email()));
    }

    /**
     * Entrega os bytes da imagem.
     *
     * <p>Cache privado de 30 dias: o conteudo de um asset nunca muda, uma troca de
     * foto gera um id novo.</p>
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> baixar(@PathVariable UUID id) {
        StoragePort.ArquivoGuardado arquivo = servico.baixar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(arquivo.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .body(arquivo.conteudo());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> apagar(@PathVariable UUID id, @AuthUser CurrentUser usuario) {
        servico.apagar(id, usuario);
        return ResponseEntity.noContent().build();
    }
}
