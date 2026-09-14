package com.piggu.rewards.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PrizeRepository extends JpaRepository<Prize, UUID> {

    List<Prize> findAllByOrderByPriceAsc();

    List<Prize> findByActiveTrueOrderByPriceAsc();
}
