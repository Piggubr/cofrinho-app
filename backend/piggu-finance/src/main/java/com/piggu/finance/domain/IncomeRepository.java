package com.piggu.finance.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface IncomeRepository extends JpaRepository<Income, UUID> {

    List<Income> findByIncomeDateBetweenOrderByIncomeDateDesc(LocalDate inicio, LocalDate fim);

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Income i WHERE i.incomeDate >= :inicio AND i.incomeDate <= :fim")
    BigDecimal somarNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
