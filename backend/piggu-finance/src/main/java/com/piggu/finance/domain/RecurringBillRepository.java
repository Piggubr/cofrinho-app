package com.piggu.finance.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecurringBillRepository extends JpaRepository<RecurringBill, UUID> {

    List<RecurringBill> findByActiveTrueOrderByDueDayAsc();
}
