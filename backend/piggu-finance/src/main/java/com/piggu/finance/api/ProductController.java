package com.piggu.finance.api;

import com.piggu.finance.api.dto.ProductResponse;
import com.piggu.finance.domain.ProductMemoryService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** Memoria de precos do mercado, ordenada pelos produtos mais comprados. */
@RestController
@RequestMapping("/api/products")
@PreAuthorize("hasAnyRole('ADMIN', 'TITULAR', 'PARCEIRO')")
public class ProductController {

    private final ProductMemoryService servico;

    public ProductController(ProductMemoryService servico) {
        this.servico = servico;
    }

    @GetMapping
    public List<ProductResponse> listar() {
        return servico.listar();
    }

    /** Preco do item contra a media dele; 204 quando o produto ainda nao tem historico. */
    @GetMapping("/price-check")
    public ResponseEntity<ProductMemoryService.ConferenciaDePreco> conferir(
            @RequestParam @Size(max = 200) String item,
            @RequestParam @DecimalMin("0") @DecimalMax("1000000") BigDecimal valor) {
        ProductMemoryService.ConferenciaDePreco conferencia = servico.conferir(item, valor);
        return conferencia == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(conferencia);
    }
}
