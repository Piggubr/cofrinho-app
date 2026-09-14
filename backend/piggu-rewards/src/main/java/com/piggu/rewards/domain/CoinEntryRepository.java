package com.piggu.rewards.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CoinEntryRepository extends JpaRepository<CoinEntry, UUID> {

    /** Saldo atual: a soma do livro-razao, calculada pelo banco. */
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CoinEntry c")
    int saldo();

    List<CoinEntry> findAllByOrderByCreatedAtDesc(Pageable pagina);
}
