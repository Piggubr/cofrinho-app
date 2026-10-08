package com.piggu.identity.domain;

import com.piggu.common.error.NotFoundException;
import com.piggu.common.web.Texto;
import com.piggu.identity.api.dto.UpdateUserRequest;
import com.piggu.identity.api.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    private final UserAccountRepository usuarios;
    private final FamiliaService familias;
    private final RefreshSessionRepository sessoes;

    public UserAdminService(UserAccountRepository usuarios,
                            FamiliaService familias,
                            RefreshSessionRepository sessoes) {
        this.usuarios = usuarios;
        this.familias = familias;
        this.sessoes = sessoes;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listar() {
        // ponytail: uma consulta de familia por conta; virar JOIN quando a lista crescer.
        return usuarios.findAllByOrderByEmailAsc().stream()
                .map(conta -> UserResponse.de(conta, familias.daConta(conta)))
                .toList();
    }

    @Transactional
    public UserResponse alterar(UUID id, UpdateUserRequest pedido) {
        UserAccount conta = usuarios.findById(id)
                .orElseThrow(() -> new NotFoundException("Conta nao encontrada."));

        if (pedido.apelido() != null) {
            conta.setNickname(Texto.limitar(pedido.apelido(), 120));
        }
        if (pedido.role() != null) {
            conta.setRole(pedido.role());
        }
        if (pedido.ativo() != null) {
            conta.setActive(pedido.ativo());
            // Desativar precisa derrubar as sessoes abertas, senao o token atual
            // continuaria valendo ate expirar.
            if (!pedido.ativo()) {
                sessoes.apagarPorUsuario(conta.getId());
            }
        }

        log.info("Conta alterada pelo admin: id={} perfil={} ativa={}", id, conta.getRole(), conta.isActive());
        return UserResponse.de(usuarios.save(conta), familias.daConta(conta));
    }
}
