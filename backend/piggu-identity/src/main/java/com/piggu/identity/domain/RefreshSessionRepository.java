package com.piggu.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {

    Optional<RefreshSession> findByTokenHash(String tokenHash);

    /**
     * Remove sessoes vencidas. Substitui {@code limparSessoesExpiradas_()}, que lia
     * todas as propriedades do script e comparava uma a uma.
     */
    @Modifying
    @Query("DELETE FROM RefreshSession s WHERE s.expiresAt < :limite")
    int apagarExpiradas(@Param("limite") Instant limite);

    @Modifying
    @Query("DELETE FROM RefreshSession s WHERE s.userId = :userId")
    int apagarPorUsuario(@Param("userId") UUID userId);
}
