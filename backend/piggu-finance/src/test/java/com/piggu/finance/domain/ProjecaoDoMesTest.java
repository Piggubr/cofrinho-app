package com.piggu.finance.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/** Projecao: o ritmo de gasto ate hoje levado ao fim do mes. */
class ProjecaoDoMesTest {

    @Test
    @DisplayName("300 gastos em 10 dias de um mes de 30 projetam 900")
    void ritmoAteHoje() {
        assertThat(RelatorioService.projetar(new BigDecimal("300"), YearMonth.of(2026, 9), LocalDate.of(2026, 9, 10)))
                .isEqualByComparingTo("900");
    }

    @Test
    @DisplayName("mes que ja passou nao tem projecao")
    void foraDoMes() {
        assertThat(RelatorioService.projetar(BigDecimal.TEN, YearMonth.of(2026, 8), LocalDate.of(2026, 9, 10))).isNull();
    }
}
