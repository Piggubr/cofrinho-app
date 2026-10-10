package com.piggu.identity.domain;

import com.piggu.common.auditoria.TrilhaDeAuditoria;
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
    private final TrilhaDeAuditoria trilha;

    public UserAdminService(UserAccountRepository usuarios,
                            FamiliaService familias,
                            RefreshSessionRepository sessoes,
                            TrilhaDeAuditoria trilha) {
        this.usuarios = usuarios;
        this.familias = familias;
        this.sessoes = sessoes;
        this.trilha = trilha;
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
        if (pedido.role() != null && pedido.role() != conta.getRole()) {
            String antes = conta.primeiroNomeExibicao() + " · " + conta.getRole().name();
            conta.setRole(pedido.role());
            // Na familia da pessoa, para o titular ver que o papel mudou e quem mudou.
            trilha.registrar(conta.getHouseholdId(), TrilhaDeAuditoria.autorDaRequisicao(),
                    TrilhaDeAuditoria.Acao.MUDOU_PAPEL, "pessoa", conta.getId(), antes,
                    conta.primeiroNomeExibicao() + " · " + conta.getRole().name());
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
