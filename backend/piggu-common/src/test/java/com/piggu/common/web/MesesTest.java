package com.piggu.common.web;

import com.piggu.common.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MesesTest {

    @Test
    @DisplayName("le AAAA-MM, mesmo com espacos em volta")
    void leMes() {
        assertThat(Meses.ouAtual("2026-10")).isEqualTo(YearMonth.of(2026, 10));
        assertThat(Meses.ouAtual(" 2025-01 ")).isEqualTo(YearMonth.of(2025, 1));
    }

    @Test
    @DisplayName("vazio ou nulo vira o mes atual de Brasilia")
    void vazioViraAtual() {
        YearMonth agora = YearMonth.now(Meses.BRASILIA);
        assertThat(Meses.ouAtual(null)).isEqualTo(agora);
        assertThat(Meses.ouAtual("  ")).isEqualTo(agora);
    }

    @Test
    @DisplayName("formato errado vira 400 em vez de 500")
    void recusaInvalido() {
        assertThatThrownBy(() -> Meses.ouAtual("10/2026"))
                .isInstanceOf(BusinessException.class)
                .extracting("codigo").isEqualTo("PEDIDO_INVALIDO");
    }
}
