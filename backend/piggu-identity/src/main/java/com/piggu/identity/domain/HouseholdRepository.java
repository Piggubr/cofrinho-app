package com.piggu.identity.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface HouseholdRepository extends JpaRepository<Household, UUID> {

    /** Trava a linha ate o fim da transacao: dois avisos de pagamento da mesma casa vao um de cada vez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Household h WHERE h.id = ?1")
    Optional<Household> travar(UUID id);
}
