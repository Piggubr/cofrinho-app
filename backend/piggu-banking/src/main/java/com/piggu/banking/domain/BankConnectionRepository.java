package com.piggu.banking.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankConnectionRepository extends JpaRepository<BankConnection, UUID> {

    Optional<BankConnection> findByPluggyItemId(String pluggyItemId);

    List<BankConnection> findByUserId(UUID userId);
}
