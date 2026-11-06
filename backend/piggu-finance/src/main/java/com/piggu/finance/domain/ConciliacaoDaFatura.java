package com.piggu.finance.domain;

import com.piggu.common.web.Texto;
import com.piggu.finance.integration.LeitorDeFatura.Linha;
import com.piggu.finance.integration.LeitorDeFatura.TipoDaLinha;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Cruza as linhas da fatura com os gastos ja lancados. Funcao pura. Na duvida, CONFIRA:
 * "ja lancada" errado esconde uma compra; "falta" errado cria gasto duplicado.
 */
public final class ConciliacaoDaFatura {

    public enum Situacao { JA_LANCADA, CONFIRA, FALTA }

    /** {@code gasto} e' nulo em {@code FALTA}. */
    public record Resultado(Linha linha, Situacao situacao, Expense gasto) {
    }

    private static final BigDecimal CENTAVO = new BigDecimal("0.01");

    private ConciliacaoDaFatura() {
    }

    public static List<Resultado> conciliar(List<Linha> linhas, List<Expense> candidatos, UUID contaId,
                                            LocalDate inicioDoPeriodo, LocalDate fimDoPeriodo) {
        List<Linha> alvo = linhas.stream().filter(l -> l.tipo() != TipoDaLinha.IGNORADA).toList();
        Resultado[] saida = new Resultado[alvo.size()];
        List<Expense> livres = new ArrayList<>(candidatos);

        // 1) mesmo id externo
        for (int i = 0; i < alvo.size(); i++) {
            String id = alvo.get(i).idExterno();
            if (id == null) {
                continue;
            }
            Expense g = livres.stream().filter(e -> id.equals(e.getExternalId())).findFirst().orElse(null);
            if (g != null) {
                saida[i] = new Resultado(alvo.get(i), Situacao.JA_LANCADA, g);
                livres.remove(g);
            }
        }

        // 2) parcelas n/m, valor com ate 1 centavo de diferenca, data no periodo da fatura (+-3 dias)
        for (int i = 0; i < alvo.size(); i++) {
            Linha l = alvo.get(i);
            if (saida[i] != null || l.parcelaNumero() == null || l.parcelaTotal() == null) {
                continue;
            }
            List<Expense> ok = livres.stream()
                    .filter(e -> e.getInstallmentNumber() != null && e.getInstallmentCount() != null
                            && e.getInstallmentNumber().intValue() == l.parcelaNumero()
                            && e.getInstallmentCount().intValue() == l.parcelaTotal()
                            && e.getAmount() != null
                            && e.getAmount().subtract(l.valor()).abs().compareTo(CENTAVO) <= 0
                            && e.getExpenseDate() != null
                            && !e.getExpenseDate().isBefore(inicioDoPeriodo.minusDays(3))
                            && !e.getExpenseDate().isAfter(fimDoPeriodo.plusDays(3)))
                    .toList();
            Expense[] melhor = melhores(l, ok, contaId);
            if (melhor.length == 1) {
                saida[i] = new Resultado(l, Situacao.JA_LANCADA, melhor[0]);
                livres.remove(melhor[0]);
            } else if (melhor.length > 1) {
                saida[i] = new Resultado(l, Situacao.CONFIRA, melhor[0]);
            }
        }

        // 3) valor exato e ate 3 dias de distancia; empate vira CONFIRA sem consumir o gasto
        for (int i = 0; i < alvo.size(); i++) {
            Linha l = alvo.get(i);
            if (saida[i] != null) {
                continue;
            }
            Expense[] melhor = melhores(l, exatosAte(l, livres, 3, 0), contaId);
            if (melhor.length == 1) {
                saida[i] = new Resultado(l, Situacao.JA_LANCADA, melhor[0]);
                livres.remove(melhor[0]);
            } else if (melhor.length > 1) {
                saida[i] = new Resultado(l, Situacao.CONFIRA, melhor[0]);
            }
        }

        // 4) valor exato de 4 a 10 dias: CONFIRA (consome o gasto); o resto falta
        for (int i = 0; i < alvo.size(); i++) {
            Linha l = alvo.get(i);
            if (saida[i] != null) {
                continue;
            }
            Expense[] melhor = melhores(l, exatosAte(l, livres, 10, 4), contaId);
            if (melhor.length > 0) {
                saida[i] = new Resultado(l, Situacao.CONFIRA, melhor[0]);
                livres.remove(melhor[0]);
            } else {
                saida[i] = new Resultado(l, Situacao.FALTA, null);
            }
        }
        return List.of(saida);
    }

    private static List<Expense> exatosAte(Linha l, List<Expense> livres, int max, int min) {
        return livres.stream()
                .filter(e -> e.getAmount() != null && e.getExpenseDate() != null
                        && e.getAmount().compareTo(l.valor()) == 0
                        && dias(l, e) >= min && dias(l, e) <= max)
                .toList();
    }

    private static long dias(Linha l, Expense e) {
        return Math.abs(ChronoUnit.DAYS.between(l.data(), e.getExpenseDate()));
    }

    /** Candidatos empatados no melhor criterio: mesma conta, menos dias, descricao mais parecida. */
    private static Expense[] melhores(Linha l, List<Expense> ok, UUID contaId) {
        Expense[] melhor = new Expense[0];
        long[] chave = null;
        List<Expense> topo = new ArrayList<>();
        for (Expense e : ok) {
            long[] c = {contaId != null && contaId.equals(e.getAccountId()) ? 1 : 0, -dias(l, e),
                    parecidos(l.descricao(), e.getItem())};
            int cmp = chave == null ? 1 : java.util.Arrays.compare(c, chave);
            if (cmp > 0) {
                chave = c;
                topo.clear();
            }
            if (cmp >= 0) {
                topo.add(e);
            }
        }
        return topo.toArray(melhor);
    }

    private static long parecidos(String a, String b) {
        Set<String> ta = tokens(a);
        ta.retainAll(tokens(b));
        return ta.size();
    }

    private static Set<String> tokens(String s) {
        Set<String> t = new HashSet<>();
        for (String p : Texto.semAcento(s).toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!p.isEmpty()) {
                t.add(p);
            }
        }
        return t;
    }
}
