package com.piggu.identity.api;

import com.piggu.identity.api.dto.AuthorizedEmailRequest;
import com.piggu.identity.api.dto.UpdateUserRequest;
import com.piggu.identity.api.dto.UserResponse;
import com.piggu.identity.domain.UserAdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Administracao de contas e da lista de e-mails liberados.
 *
 * <p>No Apps Script isso exigia editar o codigo e reimplantar. Agora e' API.</p>
 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserAdminService servico;

    public UserController(UserAdminService servico) {
        this.servico = servico;
    }

    @GetMapping
    public List<UserResponse> listar() {
        return servico.listar();
    }

    @PatchMapping("/{id}")
    public UserResponse alterar(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest pedido) {
        return servico.alterar(id, pedido);
    }

    @GetMapping("/authorized-emails")
    public List<Map<String, String>> listarAutorizados() {
        return servico.listarAutorizados();
    }

    @PostMapping("/authorized-emails")
    public ResponseEntity<Void> autorizar(@Valid @RequestBody AuthorizedEmailRequest pedido) {
        servico.autorizar(pedido);
        return ResponseEntity.status(201).build();
    }

    @DeleteMapping("/authorized-emails/{email}")
    public ResponseEntity<Void> revogar(@PathVariable String email) {
        servico.revogar(email);
        return ResponseEntity.noContent().build();
    }
}
