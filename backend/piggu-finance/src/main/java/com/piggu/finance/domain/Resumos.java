package com.piggu.finance.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Resumo curto e legivel de cada registro para a trilha de auditoria. */
final class Resumos {

    private Resumos() {
    }

    static String gasto(Expense gasto) {
        return juntar(gasto.getExpenseDate(), gasto.getItem(), gasto.getCategory(), valor(gasto.getAmount()));
    }

    static String receita(Income receita) {
        return juntar(receita.getIncomeDate(), receita.getDescription(), receita.getCategory(), valor(receita.getAmount()));
    }

    static String contaFixa(RecurringBill conta) {
        return juntar(conta.getDescription(), conta.getCategory(), valor(conta.getAmount()), "dia " + conta.getDueDay(),
                conta.isAutoLaunch() ? "automatica" : null);
    }

    static String orcamento(String categoria, BigDecimal limite) {
        return juntar(categoria, valor(limite));
    }

    static String meta(String mes, BigDecimal limite) {
        return juntar(mes, valor(limite));
    }

    static String deposito(PiggyDeposit deposito) {
        return juntar(deposito.getDepositDate(), valor(deposito.getAmount()));
    }

    private static String valor(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String juntar(Object... partes) {
        return Stream.of(partes).filter(Objects::nonNull).map(String::valueOf).filter(p -> !p.isBlank())
                .collect(Collectors.joining(" · "));
    }
}
