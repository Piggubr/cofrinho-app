package com.piggu.finance.domain;

import com.piggu.common.web.Meses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Quais avisos saem hoje e com que chave, para o lembrete nunca repetir o mesmo. */
class AvisosDoDiaTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 20);
    private static final YearMonth MES = YearMonth.of(2026, 10);

    private final OrcamentoService orcamentos = mock(OrcamentoService.class);
    private final ContasECartoes cartoes = mock(ContasECartoes.class);
    private final ContasFixasService contasFixas = mock(ContasFixasService.class);
    private final AReceberService aReceber = mock(AReceberService.class);
    private final AvisosDoDia avisos = new AvisosDoDia(orcamentos, cartoes, contasFixas, aReceber,
            Clock.fixed(HOJE.atStartOfDay(Meses.BRASILIA).plusHours(9).toInstant(), Meses.BRASILIA));

    @Test
    @DisplayName("orcamento em 80% e em 100% sao avisos diferentes; o que esta ok nao avisa")
    void orcamentos() {
        UUID mercado = UUID.randomUUID();
        UUID lazer = UUID.randomUUID();
        when(orcamentos.doMes(MES)).thenReturn(List.of(
                situacao(mercado, "Mercado", "850", "1000", "85", OrcamentoService.Alerta.ATENCAO),
                situacao(lazer, "Lazer", "320", "300", "107", OrcamentoService.Alerta.ESTOUROU),
                situacao(UUID.randomUUID(), "Casa", "10", "1000", "1", OrcamentoService.Alerta.OK)));

        assertThat(avisos.deHoje()).extracting(AvisosDoDia.Aviso::chave, AvisosDoDia.Aviso::tipo).containsExactly(
                org.assertj.core.groups.Tuple.tuple("orcamento:" + mercado + ":2026-10:80",
                        AvisosDoDia.Tipo.ORCAMENTO_ATENCAO),
                org.assertj.core.groups.Tuple.tuple("orcamento:" + lazer + ":2026-10:100",
                        AvisosDoDia.Tipo.ORCAMENTO_ESTOUROU));
    }

    @Test
    @DisplayName("fatura avisa a ate 3 dias do vencimento e so com valor; conta fixa a ate 2 dias, e de novo vencida")
    void faturasEContas() {
        UUID nubank = UUID.randomUUID();
        when(cartoes.listar()).thenReturn(List.of(
                cartao(nubank, "Nubank", HOJE.plusDays(3), "1200"),
                cartao(UUID.randomUUID(), "Longe", HOJE.plusDays(4), "50"),
                cartao(UUID.randomUUID(), "Zerada", HOJE.plusDays(1), "0"),
                new ContasECartoes.Conta(UUID.randomUUID(), "Conta", PaymentAccount.Tipo.CONTA, null, null, null, null,
                        null, false)));
        RecurringBill luz = new RecurringBill("Luz", "Casa", new BigDecimal("180"), 22, false, null);
        RecurringBill aluguel = new RecurringBill("Aluguel", "Casa", new BigDecimal("2000"), 10, false, null);
        RecurringBill internet = new RecurringBill("Internet", "Casa", new BigDecimal("100"), 28, false, null);
        when(contasFixas.doMes(MES)).thenReturn(List.of(
                new ContasFixasService.ContaDoMes(luz, HOJE.plusDays(2), ContasFixasService.Situacao.PENDENTE),
                new ContasFixasService.ContaDoMes(aluguel, HOJE.minusDays(10), ContasFixasService.Situacao.VENCIDA),
                new ContasFixasService.ContaDoMes(internet, HOJE.plusDays(8), ContasFixasService.Situacao.PENDENTE)));

        assertThat(avisos.deHoje()).extracting(AvisosDoDia.Aviso::chave).containsExactly(
                "fatura:" + nubank + ":2026-10-23",
                "conta:" + luz.getId() + ":2026-10:vencendo",
                "conta:" + aluguel.getId() + ":2026-10:vencida");
    }

    @Test
    @DisplayName("quem deve avisa so depois da data combinada, uma vez por valor")
    void aReceber() {
        Receivable atrasado = new Receivable("Joao", "Pizza", new BigDecimal("40"), HOJE.minusDays(1), null);
        Receivable hoje = new Receivable("Ana", "", new BigDecimal("10"), HOJE, null);
        when(aReceber.pendentes()).thenReturn(List.of(atrasado, hoje));

        assertThat(avisos.deHoje()).extracting(AvisosDoDia.Aviso::chave, AvisosDoDia.Aviso::assunto).containsExactly(
                org.assertj.core.groups.Tuple.tuple("receber:" + atrasado.getId(), "Joao"));
    }

    private static OrcamentoService.Situacao situacao(UUID id, String categoria, String gasto, String limite,
                                                      String percentual, OrcamentoService.Alerta alerta) {
        return new OrcamentoService.Situacao(id, categoria, new BigDecimal(limite), new BigDecimal(gasto),
                new BigDecimal(percentual), alerta);
    }

    private static ContasECartoes.Conta cartao(UUID id, String nome, LocalDate vencimento, String total) {
        ContasECartoes.Fatura fatura = new ContasECartoes.Fatura("2026-09", vencimento.minusDays(40),
                vencimento.minusDays(11), vencimento.minusDays(10), vencimento, new BigDecimal(total));
        return new ContasECartoes.Conta(id, nome, PaymentAccount.Tipo.CARTAO, (short) 13, (short) 23, null, fatura,
                null, false);
    }
}
