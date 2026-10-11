package com.piggu.finance.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PiggyDepositRepository extends JpaRepository<PiggyDeposit, UUID> {

    List<PiggyDeposit> findAllByOrderByDepositDateDesc();

    List<PiggyDeposit> findByUserIdOrderByDepositDateDesc(UUID userId);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM PiggyDeposit d")
    BigDecimal somarTudo();

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM PiggyDeposit d WHERE d.userId = ?1")
    BigDecimal somarDoUsuario(UUID userId);

    /** Instante do primeiro deposito: marca de onde comeca a contar o gasto do cofrinho. */
    @Query("SELECT MIN(d.createdAt) FROM PiggyDeposit d")
    Optional<Instant> primeiroDeposito();
}
