package com.piggu.finance.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.finance.api.dto.NoteRequest;
import com.piggu.finance.api.dto.NoteResponse;
import com.piggu.finance.domain.NoteService;
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

/** Notas e lembretes. Substitui as acoes saveNote e deleteNote. */
@Tag(name = "Notas", description = "Notas da família; nota com valor cria um gasto junto.")
@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService servico;

    public NoteController(NoteService servico) {
        this.servico = servico;
    }

    @Operation(summary = "Listar notas")
    @GetMapping
    public List<NoteResponse> listar() {
        return servico.listar();
    }

    @Operation(summary = "Criar nota")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse criar(@Valid @RequestBody NoteRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.criar(pedido, usuario.id());
    }

    @Operation(summary = "Excluir nota")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthUser CurrentUser usuario) {
        servico.excluir(id, usuario);
        return ResponseEntity.noContent().build();
    }
}
