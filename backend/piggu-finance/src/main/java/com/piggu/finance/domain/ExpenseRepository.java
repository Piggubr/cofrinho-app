package com.piggu.finance.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    List<Expense> findAllByOrderByExpenseDateDescCreatedAtDesc();

    List<Expense> findByExpenseDateBetweenOrderByExpenseDateDesc(LocalDate inicio, LocalDate fim);

    /**
     * Total gasto a partir de um instante.
     *
     * <p>O saldo do cofrinho desconta apenas os gastos registrados depois do primeiro
     * deposito. No Apps Script isso era feito carregando todos os gastos em memoria e
     * filtrando em JavaScript; aqui o banco soma.</p>
     */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.createdAt >= :desde")
    BigDecimal somarDesde(@Param("desde") Instant desde);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e "
            + "WHERE e.expenseDate >= :inicio AND e.expenseDate <= :fim")
    BigDecimal somarNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
