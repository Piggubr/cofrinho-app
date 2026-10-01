package com.piggu.identity.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.PigguRole;
import com.piggu.common.web.Texto;
import com.piggu.identity.api.dto.AuthorizedEmailRequest;
import com.piggu.identity.api.dto.UpdateUserRequest;
import com.piggu.identity.api.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    private final UserAccountRepository usuarios;
    private final AuthorizedEmailRepository autorizados;
    private final RefreshSessionRepository sessoes;

    public UserAdminService(UserAccountRepository usuarios,
                            AuthorizedEmailRepository autorizados,
                            RefreshSessionRepository sessoes) {
        this.usuarios = usuarios;
        this.autorizados = autorizados;
        this.sessoes = sessoes;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listar() {
        return usuarios.findAllByOrderByEmailAsc().stream().map(UserResponse::de).toList();
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
        return UserResponse.de(usuarios.save(conta));
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> listarAutorizados() {
        return autorizados.findAll().stream()
                .map(item -> Map.of("email", item.getEmail(), "role", item.getRole().name()))
                .toList();
    }

    @Transactional
    public void autorizar(AuthorizedEmailRequest pedido) {
        String email = Texto.email(pedido.email());
        if (email.isEmpty()) {
            throw new BusinessException("Digite um e-mail valido.");
        }
        autorizados.findByEmail(email)
                .ifPresentOrElse(
                        existente -> existente.setRole(pedido.role()),
                        () -> autorizados.save(new AuthorizedEmail(email, pedido.role())));
        // O e-mail fica fora do log: e o proprio dado pessoal sendo cadastrado.
        log.info("E-mail liberado para entrar: perfil={}", pedido.role());
    }

    @Transactional
    public void revogar(String email) {
        String normalizado = Texto.email(email);
        autorizados.findByEmail(normalizado).ifPresent(autorizados::delete);
        // A conta permanece, mas desativada: o historico de gastos e depositos
        // continua apontando para o e-mail e nao pode ficar orfao.
        usuarios.findByEmail(normalizado).ifPresent(conta -> {
            conta.setActive(false);
            usuarios.save(conta);
            sessoes.apagarPorUsuario(conta.getId());
            log.info("Acesso revogado: conta={}", conta.getId());
        });
    }

    /** Usado apenas em testes e no seed inicial. */
    @Transactional
    public UserAccount garantirConta(String email, PigguRole role) {
        return usuarios.findByEmail(email)
                .orElseGet(() -> usuarios.save(new UserAccount(Texto.email(email), role)));
    }
}
