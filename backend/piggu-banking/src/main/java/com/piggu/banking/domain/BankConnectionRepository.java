package com.piggu.banking.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankConnectionRepository extends JpaRepository<BankConnection, UUID> {

    Optional<BankConnection> findByPluggyItemId(String pluggyItemId);

    List<BankConnection> findByUserId(UUID userId);

    /** Trazer os gastos do banco tambem e usar a conexao: a retencao (D-024) so tira a que ficou parada. */
    @Modifying
    @Transactional
    @Query("UPDATE BankConnection c SET c.syncedAt = ?2 WHERE c.userId = ?1")
    int marcarUso(UUID userId, Instant quando);
}
