package com.piggu.finance.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductMemoryRepository extends JpaRepository<ProductMemory, UUID> {

    Optional<ProductMemory> findByProductKey(String productKey);

    List<ProductMemory> findAllByOrderByPurchasesDesc();
}
