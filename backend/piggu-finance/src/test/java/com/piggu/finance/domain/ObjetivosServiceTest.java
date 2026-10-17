package com.piggu.finance.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** A conta do cofrinho por objetivo, sem banco. */
class ObjetivosServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 11);

    @Test
    @DisplayName("o mes atual e o do prazo contam; prazo passado da zero meses")
    void meses() {
        assertThat(ObjetivosService.mesesAte(LocalDate.of(2026, 10, 31), HOJE)).isEqualTo(1);
        assertThat(ObjetivosService.mesesAte(LocalDate.of(2027, 3, 1), HOJE)).isEqualTo(6);
        assertThat(ObjetivosService.mesesAte(LocalDate.of(2026, 9, 30), HOJE)).isZero();
    }

    @Test
    @DisplayName("por mes arredonda para cima, para nao faltar centavo no prazo")
    void porMesArredondaParaCima() {
        var situacao = ObjetivosService.situacao(objetivo("100", LocalDate.of(2026, 12, 1)), BigDecimal.ZERO, HOJE);
        assertThat(situacao.porMes()).isEqualByComparingTo("33.34");
        assertThat(situacao.mesesRestantes()).isEqualTo(3);
    }

    @Test
    @DisplayName("prazo vencido com dinheiro faltando: atrasado, e o que falta e para ja")
    void atrasado() {
        var situacao = ObjetivosService.situacao(objetivo("500", LocalDate.of(2026, 8, 1)), new BigDecimal("200"), HOJE);
        assertThat(situacao.atrasado()).isTrue();
        assertThat(situacao.porMes()).isEqualByComparingTo("300");
    }

    @Test
    @DisplayName("guardou mais que o alvo: concluido, 100% e nada a guardar")
    void concluido() {
        var situacao = ObjetivosService.situacao(objetivo("500", LocalDate.of(2026, 8, 1)), new BigDecimal("650"), HOJE);
        assertThat(situacao.concluido()).isTrue();
        assertThat(situacao.atrasado()).isFalse();
        assertThat(situacao.percentual()).isEqualTo(100);
        assertThat(situacao.falta()).isEqualByComparingTo("0");
        assertThat(situacao.porMes()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("sem prazo nao ha conta por mes")
    void semPrazo() {
        var situacao = ObjetivosService.situacao(objetivo("500", null), new BigDecimal("125"), HOJE);
        assertThat(situacao.porMes()).isNull();
        assertThat(situacao.mesesRestantes()).isNull();
        assertThat(situacao.percentual()).isEqualTo(25);
    }

    private static SavingsGoal objetivo(String alvo, LocalDate prazo) {
        return new SavingsGoal("Viagem", new BigDecimal(alvo), prazo, null);
    }
}
