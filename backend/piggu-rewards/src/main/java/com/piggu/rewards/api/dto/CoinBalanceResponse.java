package com.piggu.rewards.api.dto;

import com.piggu.rewards.domain.CoinEntry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Saldo e ultimas movimentacoes de Fofocoins.
 *
 * @param saldo     soma de todo o livro-razao, nao apenas do historico exibido
 * @param historico as movimentacoes mais recentes, da mais nova para a mais antiga
 */
public record CoinBalanceResponse(int saldo, List<Movimento> historico) {

    public record Movimento(UUID id, Instant data, int valor, String motivo, String usuario) {

        public static Movimento de(CoinEntry lancamento) {
            return new Movimento(
                    lancamento.getId(),
                    lancamento.getCreatedAt(),
                    lancamento.getAmount(),
                    lancamento.getReason(),
                    lancamento.getSubjectUser()
            );
        }
    }
}
