package com.piggu.finance.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductMemoryRepository extends JpaRepository<ProductMemory, UUID> {

    Optional<ProductMemory> findByProductKey(String productKey);

    /** A linha de quem comprou: no job, sem token, o filtro da pessoa deixa passar a de todos. */
    Optional<ProductMemory> findByProductKeyAndUserId(String productKey, UUID userId);

    List<ProductMemory> findAllByOrderByPurchasesDesc();
}
