package com.piggu.finance.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductMemoryRepository extends JpaRepository<ProductMemory, String> {

    List<ProductMemory> findAllByOrderByPurchasesDesc();
}
