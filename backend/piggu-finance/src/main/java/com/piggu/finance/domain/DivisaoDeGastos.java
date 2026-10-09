package com.piggu.finance.domain;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Gasto dividido em partes iguais e o acerto do mes: quem pagou mais do que a propria parte. */
@Service
public class DivisaoDeGastos {

    private final ExpenseShareRepository partes;

    public DivisaoDeGastos(ExpenseShareRepository partes) {
        this.partes = partes;
    }

    /**
     * @param saldo positivo: tem a receber; negativo: deve
     */
    public record Acerto(String email, BigDecimal pagou, BigDecimal parte, BigDecimal saldo) {
    }

    /** Partes iguais; o centavo que sobra vai para a primeira pessoa. */
    void dividir(List<Expense> gastos, List<String> emails) {
        if (emails.isEmpty()) {
            return;
        }
        BigDecimal pessoas = BigDecimal.valueOf(emails.size());
        List<ExpenseShare> novas = new ArrayList<>();
        for (Expense gasto : gastos) {
            BigDecimal cada = gasto.getAmount().divide(pessoas, 2, RoundingMode.DOWN);
            BigDecimal sobra = gasto.getAmount().subtract(cada.multiply(pessoas));
            for (int i = 0; i < emails.size(); i++) {
                novas.add(new ExpenseShare(gasto.getId(), emails.get(i), i == 0 ? cada.add(sobra) : cada));
            }
        }
        partes.saveAll(novas);
    }

    @Transactional(readOnly = true)
    public List<Acerto> acerto(YearMonth mes) {
        Map<String, BigDecimal> parte = somas(partes.partesNoPeriodo(mes.atDay(1), mes.atEndOfMonth()));
        Map<String, BigDecimal> pagou = somas(partes.pagoNoPeriodo(mes.atDay(1), mes.atEndOfMonth()));
        TreeSet<String> pessoas = new TreeSet<>(parte.keySet());
        pessoas.addAll(pagou.keySet());
        return pessoas.stream().map(email -> {
            BigDecimal p = pagou.getOrDefault(email, BigDecimal.ZERO);
            BigDecimal d = parte.getOrDefault(email, BigDecimal.ZERO);
            return new Acerto(email, p, d, p.subtract(d));
        }).sorted(Comparator.comparing(Acerto::saldo).reversed()).toList();
    }

    private static Map<String, BigDecimal> somas(List<Object[]> linhas) {
        TreeMap<String, BigDecimal> mapa = new TreeMap<>();
        linhas.forEach(linha -> mapa.put((String) linha[0], (BigDecimal) linha[1]));
        return mapa;
    }
}
