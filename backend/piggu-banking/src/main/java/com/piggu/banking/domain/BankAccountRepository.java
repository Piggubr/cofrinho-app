package com.piggu.banking.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    List<BankAccount> findByConnection(BankConnection connection);

    @Query("SELECT a FROM BankAccount a JOIN FETCH a.connection c WHERE c.userEmail = ?1 ORDER BY c.institution, a.name")
    List<BankAccount> listarDoUsuario(String userEmail);
}
