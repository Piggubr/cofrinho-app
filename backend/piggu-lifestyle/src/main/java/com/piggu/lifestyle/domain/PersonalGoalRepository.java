package com.piggu.lifestyle.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonalGoalRepository extends JpaRepository<PersonalGoal, UUID> {

    List<PersonalGoal> findByUserIdAndMonthOrderByCreatedAtAsc(UUID userId, LocalDate month);

    Optional<PersonalGoal> findByIdAndUserId(UUID id, UUID userId);
}
