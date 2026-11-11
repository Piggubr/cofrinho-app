package com.piggu.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {

    Optional<RefreshSession> findByTokenHash(String tokenHash);

    List<RefreshSession> findByUserId(UUID userId);

    /**
     * Remove sessoes vencidas. Substitui {@code limparSessoesExpiradas_()}, que lia
     * todas as propriedades do script e comparava uma a uma.
     */
    @Modifying
    @Query("DELETE FROM RefreshSession s WHERE s.expiresAt < :limite")
    int apagarExpiradas(@Param("limite") Instant limite);

    /**
     * Troca o refresh da sessao sem trocar o id, que prende o aparelho de push (D-188). So vale
     * com o refresh antigo ainda nela: de duas renovacoes ao mesmo tempo, uma so passa.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE RefreshSession s SET s.tokenHash = :novo, s.userAgent = :userAgent, s.expiresAt = :expiresAt
            WHERE s.id = :id AND s.tokenHash = :antigo""")
    int rotacionar(@Param("id") UUID id, @Param("antigo") String antigo, @Param("novo") String novo,
                   @Param("userAgent") String userAgent, @Param("expiresAt") Instant expiresAt);

    @Modifying
    @Query("DELETE FROM RefreshSession s WHERE s.userId = :userId")
    int apagarPorUsuario(@Param("userId") UUID userId);
}
