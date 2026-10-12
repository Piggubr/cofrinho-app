package com.piggu.lifestyle.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GirlsMathItemRepository extends JpaRepository<GirlsMathItem, UUID> {

    List<GirlsMathItem> findByUserIdOrderByItemDateDescCreatedAtDesc(UUID userId);

    Optional<GirlsMathItem> findByIdAndUserId(UUID id, UUID userId);
}
