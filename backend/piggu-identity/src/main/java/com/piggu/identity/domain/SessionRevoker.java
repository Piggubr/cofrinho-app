package com.piggu.identity.domain;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Revoga sessoes em uma transacao propria.
 *
 * <p>Existe por causa de um detalhe que custou um teste para aparecer: no fluxo de
 * renovacao, limpar as sessoes e em seguida lancar a excecao de recusa nao limpava
 * nada. A excecao desfaz a transacao, e o DELETE ia junto.</p>
 *
 * <p>Com REQUIRES_NEW a revogacao acontece em uma transacao separada, que confirma
 * antes de a excecao derrubar a transacao de fora. Precisa ser um bean separado:
 * chamar um metodo da propria classe nao passa pelo proxy do Spring e a propagacao
 * seria ignorada em silencio.</p>
 */
@Component
public class SessionRevoker {

    private final RefreshSessionRepository sessoes;

    public SessionRevoker(RefreshSessionRepository sessoes) {
        this.sessoes = sessoes;
    }

    /** Encerra todas as sessoes abertas de uma conta. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revogarTodasDe(UUID usuarioId) {
        sessoes.apagarPorUsuario(usuarioId);
    }

    /** Encerra uma sessao especifica. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revogar(UUID sessaoId) {
        sessoes.deleteById(sessaoId);
    }
}
