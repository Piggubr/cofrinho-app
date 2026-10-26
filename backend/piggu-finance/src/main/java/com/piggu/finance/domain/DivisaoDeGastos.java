package com.piggu.finance.domain;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    public record Acerto(UUID pessoa, BigDecimal pagou, BigDecimal parte, BigDecimal saldo) {
    }

    /** Uma transferencia que zera o mes: {@code de} paga {@code valor} para {@code para}. */
    public record Transferencia(UUID de, UUID para, BigDecimal valor) {
    }

    /** Partes iguais; o centavo que sobra vai para a primeira pessoa. */
    void dividir(List<Expense> gastos, List<UUID> pessoas) {
        if (pessoas.isEmpty()) {
            return;
        }
        BigDecimal quantas = BigDecimal.valueOf(pessoas.size());
        List<ExpenseShare> novas = new ArrayList<>();
        for (Expense gasto : gastos) {
            BigDecimal cada = gasto.getAmount().divide(quantas, 2, RoundingMode.DOWN);
            BigDecimal sobra = gasto.getAmount().subtract(cada.multiply(quantas));
            for (int i = 0; i < pessoas.size(); i++) {
                novas.add(new ExpenseShare(gasto.getId(), pessoas.get(i), i == 0 ? cada.add(sobra) : cada));
            }
        }
        partes.saveAll(novas);
    }

    /** O valor do gasto mudou: as partes sao refeitas entre as mesmas pessoas. */
    void redividir(Expense gasto) {
        List<ExpenseShare> antigas = partes.findByExpenseIdOrderByAmountDesc(gasto.getId());
        partes.deleteAll(antigas);
        dividir(List.of(gasto), antigas.stream().map(ExpenseShare::getMemberId).toList());
    }

    @Transactional(readOnly = true)
    public List<Acerto> acerto(YearMonth mes) {
        Map<UUID, BigDecimal> parte = somas(partes.partesNoPeriodo(mes.atDay(1), mes.atEndOfMonth()));
        Map<UUID, BigDecimal> pagou = somas(partes.pagoNoPeriodo(mes.atDay(1), mes.atEndOfMonth()));
        // Pessoa nula: conta excluida; o que ela pagou ou devia continua na conta da familia.
        Set<UUID> pessoas = new LinkedHashSet<>(parte.keySet());
        pessoas.addAll(pagou.keySet());
        return pessoas.stream().map(pessoa -> {
            BigDecimal p = pagou.getOrDefault(pessoa, BigDecimal.ZERO);
            BigDecimal d = parte.getOrDefault(pessoa, BigDecimal.ZERO);
            return new Acerto(pessoa, p, d, p.subtract(d));
        }).sorted(Comparator.comparing(Acerto::saldo).reversed()).toList();
    }

    /**
     * Quem deve quanto a quem no mes, com o menor numero de transferencias: quem mais deve
     * paga quem mais tem a receber, ate todo mundo zerar. No casal e uma transferencia so.
     */
    @Transactional(readOnly = true)
    public List<Transferencia> quemDeveAQuem(YearMonth mes) {
        return transferencias(acerto(mes));
    }

    static List<Transferencia> transferencias(List<Acerto> saldos) {
        Deque<Acerto> credores = new ArrayDeque<>(saldos.stream()
                .filter(a -> a.pessoa() != null && a.saldo().signum() > 0)
                .sorted(Comparator.comparing(Acerto::saldo).reversed()).toList());
        Deque<Acerto> devedores = new ArrayDeque<>(saldos.stream()
                .filter(a -> a.pessoa() != null && a.saldo().signum() < 0)
                .sorted(Comparator.comparing(Acerto::saldo)).toList());
        Map<UUID, BigDecimal> falta = new HashMap<>();
        saldos.forEach(a -> falta.put(a.pessoa(), a.saldo().abs()));
        List<Transferencia> resultado = new ArrayList<>();
        while (!credores.isEmpty() && !devedores.isEmpty()) {
            UUID credor = credores.peek().pessoa();
            UUID devedor = devedores.peek().pessoa();
            BigDecimal valor = falta.get(credor).min(falta.get(devedor));
            if (valor.signum() > 0) {
                resultado.add(new Transferencia(devedor, credor, valor));
            }
            falta.merge(credor, valor.negate(), BigDecimal::add);
            falta.merge(devedor, valor.negate(), BigDecimal::add);
            if (falta.get(credor).signum() == 0) {
                credores.pop();
            }
            if (falta.get(devedor).signum() == 0) {
                devedores.pop();
            }
        }
        return resultado;
    }

    private static Map<UUID, BigDecimal> somas(List<Object[]> linhas) {
        Map<UUID, BigDecimal> mapa = new HashMap<>();
        linhas.forEach(linha -> mapa.put((UUID) linha[0], (BigDecimal) linha[1]));
        return mapa;
    }
}
