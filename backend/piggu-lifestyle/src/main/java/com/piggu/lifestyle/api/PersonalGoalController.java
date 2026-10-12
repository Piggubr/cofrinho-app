package com.piggu.lifestyle.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Meses;
import com.piggu.lifestyle.api.dto.PersonalGoalRequest;
import com.piggu.lifestyle.api.dto.PersonalGoalResponse;
import com.piggu.lifestyle.domain.PersonalGoalService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Metas do mes. Cada pessoa so ve as proprias, entao todos os papeis usam.
 */
@RestController
@RequestMapping("/api/personal-goals")
@PreAuthorize("hasAnyRole('ADMIN', 'TITULAR', 'PARCEIRO', 'MEMBRO')")
public class PersonalGoalController {

    private final PersonalGoalService servico;

    public PersonalGoalController(PersonalGoalService servico) {
        this.servico = servico;
    }

    /** @param mes AAAA-MM; vazio e o mes atual de Brasilia */
    @GetMapping
    public List<PersonalGoalResponse> listar(@RequestParam(required = false) String mes,
                                             @AuthUser CurrentUser usuario) {
        return servico.listar(Meses.ouAtual(mes), usuario.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PersonalGoalResponse criar(@Valid @RequestBody PersonalGoalRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.criar(pedido, usuario.id());
    }

    @PutMapping("/{id}")
    public PersonalGoalResponse atualizar(@PathVariable UUID id,
                                          @Valid @RequestBody PersonalGoalRequest pedido,
                                          @AuthUser CurrentUser usuario) {
        return servico.atualizar(id, pedido, usuario.id());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthUser CurrentUser usuario) {
        servico.excluir(id, usuario.id());
        return ResponseEntity.noContent().build();
    }
}
