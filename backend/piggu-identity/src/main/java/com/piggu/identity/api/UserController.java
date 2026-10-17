package com.piggu.identity.api;

import com.piggu.identity.api.dto.UpdateUserRequest;
import com.piggu.identity.api.dto.UserResponse;
import com.piggu.identity.domain.UserAdminService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Administracao das contas da instalacao: listar, ativar e desativar.
 *
 * <p>O cadastro e aberto; quem entra em cada familia e assunto do titular
 * ({@link FamiliaController}). Isto aqui e operacao, so do ADMIN.</p>
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
}
