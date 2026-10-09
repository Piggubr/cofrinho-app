package com.piggu.finance.domain;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;

/** Contas do mes e do ano, feitas pelo banco: somas, nunca saldos guardados. */
@Service
public class RelatorioService {

    private final ExpenseRepository gastos;
    private final IncomeRepository receitas;

    public RelatorioService(ExpenseRepository gastos, IncomeRepository receitas) {
        this.gastos = gastos;
        this.receitas = receitas;
    }

    /**
     * @param taxaDePoupanca sobra sobre receitas, em porcentagem; nula sem receita no mes
     */
    public record ResumoDoMes(String mes, BigDecimal receitas, BigDecimal gastos, BigDecimal sobra,
                              BigDecimal taxaDePoupanca) {
    }

    @Transactional(readOnly = true)
    public ResumoDoMes resumo(YearMonth mes) {
        BigDecimal entrou = receitas.somarNoPeriodo(mes.atDay(1), mes.atEndOfMonth());
        BigDecimal saiu = gastos.somarNoPeriodo(mes.atDay(1), mes.atEndOfMonth());
        BigDecimal sobra = entrou.subtract(saiu);
        BigDecimal taxa = entrou.signum() > 0
                ? sobra.multiply(BigDecimal.valueOf(100)).divide(entrou, 1, RoundingMode.HALF_UP)
                : null;
        return new ResumoDoMes(mes.toString(), entrou, saiu, sobra, taxa);
    }
}
