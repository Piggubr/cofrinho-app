package com.piggu.finance.api;

import com.piggu.finance.api.dto.ProductResponse;
import com.piggu.finance.domain.ProductMemoryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Memoria de precos do mercado, ordenada pelos produtos mais comprados. */
@RestController
@RequestMapping("/api/products")
@PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
public class ProductController {

    private final ProductMemoryService servico;

    public ProductController(ProductMemoryService servico) {
        this.servico = servico;
    }

    @GetMapping
    public List<ProductResponse> listar() {
        return servico.listar();
    }
}
