package com.piggu.finance.domain;

import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Meses;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.IntStream;

/** Contas do mes e do ano, feitas pelo banco: somas, nunca saldos guardados. */
@Service
public class RelatorioService {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final ExpenseRepository gastos;
    private final IncomeRepository receitas;

    public RelatorioService(ExpenseRepository gastos, IncomeRepository receitas) {
        this.gastos = gastos;
        this.receitas = receitas;
    }

    /**
     * @param taxaDePoupanca   sobra sobre receitas, em porcentagem; nula sem receita no mes
     * @param variacao         gastos contra o mes anterior, em porcentagem; nula se o anterior nao teve gasto
     * @param projecaoDeGastos so no mes corrente: o ritmo ate hoje levado ao fim do mes
     */
    public record ResumoDoMes(String mes, BigDecimal receitas, BigDecimal gastos, BigDecimal sobra,
                              BigDecimal taxaDePoupanca, BigDecimal gastosMesAnterior, BigDecimal variacao,
                              BigDecimal projecaoDeGastos, List<CategoriaNoMes> porCategoria) {
    }

    public record CategoriaNoMes(String categoria, BigDecimal total, BigDecimal anterior) {
    }

    public record MesDoAno(String mes, BigDecimal receitas, BigDecimal gastos, BigDecimal sobra) {
    }

    public record CategoriaNoAno(String categoria, BigDecimal total) {
    }

    public record RelatorioDoAno(int ano, BigDecimal receitas, BigDecimal gastos, BigDecimal sobra,
                                 List<MesDoAno> meses, List<CategoriaNoAno> porCategoria) {
    }

    @Transactional(readOnly = true)
    public ResumoDoMes resumo(YearMonth mes) {
        return resumo(mes, LocalDate.now(Meses.BRASILIA));
    }

    ResumoDoMes resumo(YearMonth mes, LocalDate hoje) {
        BigDecimal entrou = receitas.somarNoPeriodo(mes.atDay(1), mes.atEndOfMonth());
        Map<String, BigDecimal> agora = gastos.somarPorCategoria(mes.atDay(1), mes.atEndOfMonth());
        YearMonth anterior = mes.minusMonths(1);
        Map<String, BigDecimal> antes = gastos.somarPorCategoria(anterior.atDay(1), anterior.atEndOfMonth());

        BigDecimal saiu = somar(agora);
        BigDecimal saiuAntes = somar(antes);
        BigDecimal sobra = entrou.subtract(saiu);
        List<CategoriaNoMes> porCategoria = new TreeSet<>(agora.keySet()).stream()
                .map(c -> new CategoriaNoMes(c, agora.get(c), antes.getOrDefault(c, BigDecimal.ZERO)))
                .sorted(Comparator.comparing(CategoriaNoMes::total).reversed())
                .toList();
        return new ResumoDoMes(mes.toString(), entrou, saiu, sobra, porcentagem(sobra, entrou), saiuAntes,
                porcentagem(saiu.subtract(saiuAntes), saiuAntes), projetar(saiu, mes, hoje), porCategoria);
    }

    /** Relatorio do ano, mes a mes e por categoria. Premium. */
    @Transactional(readOnly = true)
    public RelatorioDoAno doAno(Year ano, CurrentUser usuario) {
        usuario.exigirPremium("Relatório anual");
        // ponytail: duas somas por mes (24 consultas); um GROUP BY por mes se o relatorio ficar lento.
        List<MesDoAno> meses = IntStream.rangeClosed(1, 12).mapToObj(ano::atMonth).map(mes -> {
            BigDecimal entrou = receitas.somarNoPeriodo(mes.atDay(1), mes.atEndOfMonth());
            BigDecimal saiu = gastos.somarNoPeriodo(mes.atDay(1), mes.atEndOfMonth());
            return new MesDoAno(mes.toString(), entrou, saiu, entrou.subtract(saiu));
        }).toList();
        List<CategoriaNoAno> porCategoria = gastos.somarPorCategoria(ano.atDay(1), ano.atMonth(12).atEndOfMonth())
                .entrySet().stream()
                .map(e -> new CategoriaNoAno(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(CategoriaNoAno::total).reversed().thenComparing(CategoriaNoAno::categoria))
                .toList();
        BigDecimal entrou = meses.stream().map(MesDoAno::receitas).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saiu = meses.stream().map(MesDoAno::gastos).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RelatorioDoAno(ano.getValue(), entrou, saiu, entrou.subtract(saiu), meses, porCategoria);
    }

    /** Gasto ate hoje dividido pelos dias que passaram, vezes os dias do mes. Nulo fora do mes corrente. */
    static BigDecimal projetar(BigDecimal gastoAteHoje, YearMonth mes, LocalDate hoje) {
        if (!YearMonth.from(hoje).equals(mes)) {
            return null;
        }
        return gastoAteHoje.multiply(BigDecimal.valueOf(mes.lengthOfMonth()))
                .divide(BigDecimal.valueOf(hoje.getDayOfMonth()), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal somar(Map<String, BigDecimal> porCategoria) {
        return porCategoria.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal porcentagem(BigDecimal parte, BigDecimal todo) {
        return todo.signum() > 0 ? parte.multiply(CEM).divide(todo, 1, RoundingMode.HALF_UP) : null;
    }
}
