package com.piggu.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    List<UserAccount> findAllByOrderByEmailAsc();

    List<UserAccount> findByHouseholdIdOrderByCreatedAtAsc(UUID householdId);

    /** Sem nenhum acesso desde o limite (conta que nunca entrou conta pela criacao). */
    @Query("SELECT u.id FROM UserAccount u WHERE COALESCE(u.lastLoginAt, u.createdAt) < :limite")
    List<UUID> semAcessoDesde(@Param("limite") Instant limite);

    @Query("SELECT u.id FROM UserAccount u WHERE u.active = false AND u.deactivatedAt < :limite")
    List<UUID> desativadasDesde(@Param("limite") Instant limite);
}
