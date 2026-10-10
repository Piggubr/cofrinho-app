package com.piggu.lifestyle.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.lifestyle.api.dto.PlaceRequest;
import com.piggu.lifestyle.api.dto.PlaceResponse;
import com.piggu.lifestyle.api.dto.PlaceTagRequest;
import com.piggu.lifestyle.domain.PlaceService;
import com.piggu.lifestyle.domain.PlaceTagService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Lugares visitados. Substitui savePlace, deletePlace, getPlacePhoto e addPlaceTag.
 *
 * <p>A foto nao volta mais em base64 dentro da resposta: o front recebe o
 * identificador e busca a imagem direto no servico de media, que sabe fazer cache.</p>
 */
@RestController
@RequestMapping("/api/places")
@PreAuthorize("hasAnyRole('ADMIN', 'TITULAR', 'PARCEIRO')")
public class PlaceController {

    private final PlaceService servico;
    private final PlaceTagService marcadores;

    public PlaceController(PlaceService servico, PlaceTagService marcadores) {
        this.servico = servico;
        this.marcadores = marcadores;
    }

    @GetMapping
    public List<PlaceResponse> listar() {
        return servico.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlaceResponse criar(@Valid @RequestBody PlaceRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.criar(pedido, usuario.email());
    }

    @PutMapping("/{id}")
    public PlaceResponse atualizar(@PathVariable UUID id,
                                   @Valid @RequestBody PlaceRequest pedido,
                                   @AuthUser CurrentUser usuario) {
        return servico.atualizar(id, pedido, usuario);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthUser CurrentUser usuario) {
        servico.excluir(id, usuario);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tags")
    public List<String> listarMarcadores() {
        return marcadores.listar();
    }

    @PostMapping("/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public List<String> criarMarcador(@Valid @RequestBody PlaceTagRequest pedido,
                                      @AuthUser CurrentUser usuario) {
        marcadores.criar(pedido.nome(), usuario.email());
        return marcadores.listar();
    }
}
