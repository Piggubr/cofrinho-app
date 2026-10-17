package com.piggu.finance.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, UUID> {

    /** Parte de cada pessoa nos gastos divididos do periodo. */
    @Query("SELECT s.memberId, SUM(s.amount) FROM ExpenseShare s, Expense e "
            + "WHERE s.expenseId = e.id AND e.expenseDate >= :inicio AND e.expenseDate <= :fim GROUP BY s.memberId")
    List<Object[]> partesNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /** Quanto cada pessoa pagou de gastos divididos no periodo (a soma das partes do que ela lancou). */
    @Query("SELECT e.userId, SUM(s.amount) FROM ExpenseShare s, Expense e "
            + "WHERE s.expenseId = e.id AND e.expenseDate >= :inicio AND e.expenseDate <= :fim GROUP BY e.userId")
    List<Object[]> pagoNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
