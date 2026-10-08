package com.piggu.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HouseholdInviteRepository extends JpaRepository<HouseholdInvite, UUID> {

    /** Mais recente primeiro: se mais de uma familia convidou, vale o ultimo convite. */
    List<HouseholdInvite> findByEmailOrderByCreatedAtDesc(String email);

    List<HouseholdInvite> findByHouseholdIdOrderByCreatedAtDesc(UUID householdId);

    Optional<HouseholdInvite> findByHouseholdIdAndEmail(UUID householdId, String email);
}
