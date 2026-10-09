package com.piggu.finance.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.finance.api.dto.CategoriesResponse;
import com.piggu.finance.api.dto.CategoryRequest;
import com.piggu.finance.domain.CategoryService;
import com.piggu.finance.domain.RegrasDeCategoria;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

/** Categorias de gasto e as regras de categoria automatica. */
@RestController
@RequestMapping("/api/categories")
@PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
public class CategoryController {

    private final CategoryService servico;
    private final RegrasDeCategoria regras;

    public CategoryController(CategoryService servico, RegrasDeCategoria regras) {
        this.servico = servico;
        this.regras = regras;
    }

    public record RuleRequest(
            @NotBlank(message = "Digite o termo.") @Size(max = 100) String termo,
            @NotBlank(message = "Escolha a categoria.") @Size(max = 50) String categoria) {
    }

    @GetMapping
    public CategoriesResponse listar() {
        return new CategoriesResponse(servico.listar());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriesResponse criar(@Valid @RequestBody CategoryRequest pedido,
                                    @AuthUser CurrentUser usuario) {
        servico.criar(pedido.nome(), usuario.email());
        return new CategoriesResponse(servico.listar());
    }

    @GetMapping("/rules")
    public List<RegrasDeCategoria.Regra> regras() {
        return regras.listar();
    }

    /** Cria a regra; se o termo ja tem uma, troca a categoria. */
    @PutMapping("/rules")
    public RegrasDeCategoria.Regra definirRegra(@Valid @RequestBody RuleRequest pedido, @AuthUser CurrentUser usuario) {
        return regras.definir(pedido.termo(), pedido.categoria(), usuario.email());
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> excluirRegra(@PathVariable UUID id) {
        regras.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
