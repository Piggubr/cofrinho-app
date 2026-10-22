package com.piggu.finance.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, UUID> {

    List<SavingsGoal> findAllByOrderByCreatedAtAsc();

    /** Os criados pela pessoa: o compartilhado pelo parceiro nao gasta o cofrinho do gratuito dela. */
    long countByUserId(UUID userId);
}
