package com.piggu.finance.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/** Projecao (D-190): os gastos fixos entram uma vez; os outros seguem o ritmo do mes. */
class ProjecaoDoMesTest {

    private static final YearMonth SETEMBRO = YearMonth.of(2026, 9);
    private static final LocalDate DIA_10 = LocalDate.of(2026, 9, 10);

    @Test
    @DisplayName("300 variaveis em 10 dias de um mes de 30 projetam 900")
    void ritmoAteHoje() {
        assertThat(RelatorioService.projetar(BigDecimal.ZERO, new BigDecimal("300"), BigDecimal.ZERO, SETEMBRO, DIA_10))
                .isEqualByComparingTo("900");
    }

    @Test
    @DisplayName("o aluguel de 2.000 lancado no dia 5 conta uma vez, nao vezes 3 (C-2 da auditoria)")
    void fixoContaUmaVez() {
        assertThat(RelatorioService.projetar(new BigDecimal("2000"), new BigDecimal("300"), BigDecimal.ZERO, SETEMBRO,
                DIA_10)).isEqualByComparingTo("2900");
    }

    @Test
    @DisplayName("variavel ja lancada para depois de hoje conta uma vez: a projecao nunca fica abaixo do gasto")
    void variavelFuturaContaUmaVez() {
        assertThat(RelatorioService.projetar(BigDecimal.ZERO, new BigDecimal("300"), new BigDecimal("50"), SETEMBRO,
                DIA_10)).isEqualByComparingTo("950");
    }

    @Test
    @DisplayName("mes que ja passou nao tem projecao")
    void foraDoMes() {
        assertThat(RelatorioService.projetar(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO, YearMonth.of(2026, 8),
                DIA_10)).isNull();
    }

    @Test
    @DisplayName("mesmo dia do mes anterior: dia 31 de marco compara ate 28 de fevereiro")
    void mesmoDiaDoAnterior() {
        assertThat(RelatorioService.mesmoDia(YearMonth.of(2026, 2), LocalDate.of(2026, 3, 31)))
                .isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(RelatorioService.mesmoDia(YearMonth.of(2026, 8), DIA_10)).isEqualTo(LocalDate.of(2026, 8, 10));
    }
}
