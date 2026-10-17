package com.piggu.finance.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentAccountRepository extends JpaRepository<PaymentAccount, UUID> {

    List<PaymentAccount> findAllByOrderByNameAsc();

    /** Por pessoa: o titular ve as contas dos menores, mas pode ter uma com o mesmo nome. */
    boolean existsByNameIgnoreCaseAndUserId(String name, UUID userId);

}
