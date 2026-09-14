package com.piggu.finance.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.finance.api.dto.NoteRequest;
import com.piggu.finance.api.dto.NoteResponse;
import com.piggu.finance.domain.NoteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

import java.util.List;
import java.util.UUID;

/** Notas e lembretes. Substitui as acoes saveNote e deleteNote. */
@RestController
@RequestMapping("/api/notes")
@PreAuthorize("hasAnyRole('ADMIN', 'BEATRIZ')")
public class NoteController {

    private final NoteService servico;

    public NoteController(NoteService servico) {
        this.servico = servico;
    }

    @GetMapping
    public List<NoteResponse> listar() {
        return servico.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse criar(@Valid @RequestBody NoteRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.criar(pedido, usuario.email());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthUser CurrentUser usuario) {
        servico.excluir(id, usuario);
        return ResponseEntity.noContent().build();
    }
}
