package com.piggu.finance.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.finance.api.dto.CategoriesResponse;
import com.piggu.finance.api.dto.CategoryRequest;
import com.piggu.finance.domain.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Categorias de gasto. Substitui a acao addCategory. */
@RestController
@RequestMapping("/api/categories")
@PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
public class CategoryController {

    private final CategoryService servico;

    public CategoryController(CategoryService servico) {
        this.servico = servico;
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
}
