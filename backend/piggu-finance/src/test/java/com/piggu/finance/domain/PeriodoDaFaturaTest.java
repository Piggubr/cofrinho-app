package com.piggu.finance.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/** Fatura: do dia seguinte ao fechamento anterior ate o fechamento; vencimento no mesmo mes ou no seguinte. */
class PeriodoDaFaturaTest {

    @Test
    @DisplayName("fecha dia 5 e vence dia 12: fatura de setembro vai de 6/ago a 5/set e vence 12/set")
    void vencimentoNoMesmoMes() {
        ContasECartoes.Periodo periodo = ContasECartoes.periodo(5, 12, YearMonth.of(2026, 9));
        assertThat(periodo.inicio()).isEqualTo(LocalDate.of(2026, 8, 6));
        assertThat(periodo.fechamento()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(periodo.vencimento()).isEqualTo(LocalDate.of(2026, 9, 12));
    }

    @Test
    @DisplayName("fecha dia 25 e vence dia 3: vence no mes seguinte; dia 31 vira o fim de fevereiro")
    void vencimentoNoMesSeguinteEMesCurto() {
        assertThat(ContasECartoes.periodo(25, 3, YearMonth.of(2026, 9)).vencimento()).isEqualTo(LocalDate.of(2026, 10, 3));
        ContasECartoes.Periodo marco = ContasECartoes.periodo(31, 10, YearMonth.of(2026, 3));
        assertThat(marco.inicio()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(marco.fechamento()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    @DisplayName("compra no dia do fechamento ainda entra na fatura do mes; no dia seguinte, na do proximo")
    void faturaAberta() {
        assertThat(ContasECartoes.mesDaFaturaAberta(5, LocalDate.of(2026, 9, 5))).isEqualTo(YearMonth.of(2026, 9));
        assertThat(ContasECartoes.mesDaFaturaAberta(5, LocalDate.of(2026, 9, 6))).isEqualTo(YearMonth.of(2026, 10));
    }
}
