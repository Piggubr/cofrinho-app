package com.piggu.lifestyle.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.lifestyle.api.dto.GirlsMathRequest;
import com.piggu.lifestyle.api.dto.GirlsMathResponse;
import com.piggu.lifestyle.domain.GirlsMathService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Girls Math: o que a pessoa decidiu nao comprar. Cada pessoa so ve os proprios itens,
 * entao todos os papeis usam.
 */
@Tag(name = "Girls Math", description = "O que a pessoa decidiu não comprar.")
@RestController
@RequestMapping("/api/girls-math")
public class GirlsMathController {

    private final GirlsMathService servico;

    public GirlsMathController(GirlsMathService servico) {
        this.servico = servico;
    }

    @Operation(summary = "Listar itens do Girls Math")
    @GetMapping
    public List<GirlsMathResponse> listar(@AuthUser CurrentUser usuario) {
        return servico.listar(usuario.id());
    }

    @Operation(summary = "Criar item do Girls Math")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GirlsMathResponse criar(@Valid @RequestBody GirlsMathRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.criar(pedido, usuario);
    }

    @Operation(summary = "Excluir item do Girls Math")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthUser CurrentUser usuario) {
        servico.excluir(id, usuario.id());
        return ResponseEntity.noContent().build();
    }
}
