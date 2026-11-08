package com.piggu.identity.api;

import com.piggu.identity.api.dto.UpdateUserRequest;
import com.piggu.identity.api.dto.UserResponse;
import com.piggu.identity.domain.CodigoDoAdmin;
import com.piggu.identity.domain.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Administracao das contas da instalacao: listar, ativar e desativar.
 *
 * <p>O cadastro e aberto; quem entra em cada familia e assunto do titular
 * ({@link FamiliaController}). Isto aqui e operacao, so do ADMIN, e cada chamada leva o codigo
 * do app autenticador ({@link CodigoDoAdmin}, D-182): a lista tem o e-mail de todas as contas.</p>
 */
@Tag(name = "Administração", description = "Contas da instalação. Só para ADMIN.")
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserAdminService servico;
    private final CodigoDoAdmin codigoDoAdmin;

    public UserController(UserAdminService servico, CodigoDoAdmin codigoDoAdmin) {
        this.servico = servico;
        this.codigoDoAdmin = codigoDoAdmin;
    }

    @Operation(summary = "Listar as contas (admin)")
    @GetMapping
    public List<UserResponse> listar(@RequestHeader(name = CodigoDoAdmin.CABECALHO, required = false) String codigo) {
        codigoDoAdmin.exigir(codigo);
        return servico.listar();
    }

    @Operation(summary = "Ativar, desativar ou mudar uma conta (admin)")
    @PatchMapping("/{id}")
    public UserResponse alterar(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest pedido,
                                @RequestHeader(name = CodigoDoAdmin.CABECALHO, required = false) String codigo) {
        codigoDoAdmin.exigir(codigo);
        return servico.alterar(id, pedido);
    }
}
