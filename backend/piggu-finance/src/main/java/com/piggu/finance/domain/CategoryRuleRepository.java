package com.piggu.finance.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, UUID> {

    List<CategoryRule> findAllByOrderByTermAsc();

    Optional<CategoryRule> findByTerm(String term);
}
