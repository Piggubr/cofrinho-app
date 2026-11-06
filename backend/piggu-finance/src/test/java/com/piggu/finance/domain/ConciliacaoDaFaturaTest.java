package com.piggu.finance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.piggu.finance.domain.ConciliacaoDaFatura.Resultado;
import com.piggu.finance.domain.ConciliacaoDaFatura.Situacao;
import com.piggu.finance.integration.LeitorDeFatura.Linha;
import com.piggu.finance.integration.LeitorDeFatura.TipoDaLinha;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConciliacaoDaFaturaTest {

    private static final UUID CARTAO = UUID.randomUUID();
    private static final LocalDate INI = LocalDate.of(2026, 10, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 10, 31);

    private static Linha linha(String data, String desc, String valor, Integer n, Integer m, String idExterno) {
        return new Linha(LocalDate.parse(data), desc, new BigDecimal(valor), TipoDaLinha.COMPRA, n, m, idExterno);
    }

    private static Linha linha(String data, String desc, String valor) {
        return linha(data, desc, valor, null, null, null);
    }

    private static Expense gasto(String data, String item, String valor, UUID conta) {
        Expense e = new Expense(LocalDate.parse(data), null, item, item, "Outros", new BigDecimal(valor),
                "expense", "manual", UUID.randomUUID());
        e.pagarCom(conta);
        return e;
    }

    private static List<Resultado> rodar(List<Linha> linhas, Expense... gastos) {
        return ConciliacaoDaFatura.conciliar(linhas, List.of(gastos), CARTAO, INI, FIM);
    }

    @Test
    void valorIgualDoisDiasJaLancada() {
        Expense g = gasto("2026-10-12", "Mercado", "50.00", CARTAO);
        Resultado r = rodar(List.of(linha("2026-10-10", "MERCADO", "50.00")), g).get(0);
        assertThat(r.situacao()).isEqualTo(Situacao.JA_LANCADA);
        assertThat(r.gasto()).isSameAs(g);
    }

    @Test
    void doisCandidatosGanhaOMesmoCartao() {
        Expense outro = gasto("2026-10-10", "Mercado", "50.00", UUID.randomUUID());
        Expense certo = gasto("2026-10-11", "Mercado", "50.00", CARTAO);
        Resultado r = rodar(List.of(linha("2026-10-10", "Mercado", "50.00")), outro, certo).get(0);
        assertThat(r.situacao()).isEqualTo(Situacao.JA_LANCADA);
        assertThat(r.gasto()).isSameAs(certo);
    }

    @Test
    void empateVaiParaConfira() {
        Expense a = gasto("2026-10-09", "Padaria", "20.00", CARTAO);
        Expense b = gasto("2026-10-11", "Padaria", "20.00", CARTAO);
        Resultado r = rodar(List.of(linha("2026-10-10", "Padaria", "20.00")), a, b).get(0);
        assertThat(r.situacao()).isEqualTo(Situacao.CONFIRA);
        assertThat(r.gasto()).isIn(a, b);
    }

    @Test
    void seisDiasConfira() {
        Expense g = gasto("2026-10-16", "Mercado", "50.00", CARTAO);
        Resultado r = rodar(List.of(linha("2026-10-10", "Mercado", "50.00")), g).get(0);
        assertThat(r.situacao()).isEqualTo(Situacao.CONFIRA);
        assertThat(r.gasto()).isSameAs(g);
    }

    @Test
    void parcelaComUmCentavoDeDiferenca() {
        Expense g = gasto("2026-10-20", "Geladeira 3/10", "99.99", CARTAO);
        g.parcela(3, 10);
        Resultado r = rodar(List.of(linha("2026-08-20", "GELADEIRA", "100.00", 3, 10, null)), g).get(0);
        assertThat(r.situacao()).isEqualTo(Situacao.JA_LANCADA);
        assertThat(r.gasto()).isSameAs(g);
    }

    @Test
    void parcelaForaDoPeriodoFalta() {
        Expense g = gasto("2026-12-20", "Geladeira", "100.00", CARTAO);
        g.parcela(3, 10);
        Resultado r = rodar(List.of(linha("2026-08-20", "GELADEIRA", "100.00", 3, 10, null)), g).get(0);
        assertThat(r.situacao()).isEqualTo(Situacao.FALTA);
        assertThat(r.gasto()).isNull();
    }

    @Test
    void externalIdJaImportado() {
        Expense g = gasto("2026-01-01", "Qualquer", "1.00", CARTAO);
        g.importadoDe("abc");
        Resultado r = rodar(List.of(linha("2026-10-10", "X", "77.00", null, null, "abc")), g).get(0);
        assertThat(r.situacao()).isEqualTo(Situacao.JA_LANCADA);
        assertThat(r.gasto()).isSameAs(g);
    }

    @Test
    void mesmoGastoNaoServeDuasVezes() {
        Expense g = gasto("2026-10-10", "Mercado", "50.00", CARTAO);
        List<Resultado> r = rodar(List.of(linha("2026-10-10", "Mercado", "50.00"),
                linha("2026-10-10", "Mercado", "50.00")), g);
        assertThat(r).extracting(Resultado::situacao).containsExactly(Situacao.JA_LANCADA, Situacao.FALTA);
    }

    @Test
    void centavosDiferentesFalta() {
        Expense g = gasto("2026-10-10", "Mercado", "50.01", CARTAO);
        Resultado r = rodar(List.of(linha("2026-10-10", "Mercado", "50.00")), g).get(0);
        assertThat(r.situacao()).isEqualTo(Situacao.FALTA);
    }
}
