package com.piggu.lifestyle.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.lifestyle.api.dto.CatalogProduct;
import com.piggu.lifestyle.api.dto.ShoppingItemRequest;
import com.piggu.lifestyle.api.dto.ShoppingItemResponse;
import com.piggu.lifestyle.domain.ShoppingService;
import com.piggu.lifestyle.integration.CatalogClient;
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

/**
 * Listas de compras e de desejos, e a busca no catalogo de produtos.
 *
 * <p>Substitui saveShoppingItem, toggleShoppingItem, deleteShoppingItem e
 * searchProducts.</p>
 */
@RestController
@RequestMapping("/api/shopping")
@PreAuthorize("hasAnyRole('ADMIN', 'TITULAR', 'PARCEIRO')")
public class ShoppingController {

    private final ShoppingService servico;
    private final CatalogClient catalogo;

    public ShoppingController(ShoppingService servico, CatalogClient catalogo) {
        this.servico = servico;
        this.catalogo = catalogo;
    }

    /** @param lista filtro opcional: Compras ou Desejos */
    @GetMapping("/items")
    public List<ShoppingItemResponse> listar(@RequestParam(required = false) String lista) {
        return servico.listar(lista);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingItemResponse criar(@Valid @RequestBody ShoppingItemRequest pedido,
                                      @AuthUser CurrentUser usuario) {
        return servico.criar(pedido, usuario.id());
    }

    @PatchMapping("/items/{id}/purchased")
    public ShoppingItemResponse alternar(@PathVariable UUID id, @RequestParam boolean comprado) {
        return servico.alternarComprado(id, comprado);
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthUser CurrentUser usuario) {
        servico.excluir(id, usuario);
        return ResponseEntity.noContent().build();
    }

    /** Busca no catalogo aberto de produtos, para preencher marca, imagem e codigo. */
    @GetMapping("/catalog")
    public List<CatalogProduct> buscarNoCatalogo(@RequestParam String busca) {
        return catalogo.buscar(busca);
    }
}
