package com.piggu.finance.domain;

import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;

/** Limite de gasto por mes. */
class MonthlyGoalServiceTest extends PostgresIntegrationTest {

    private static final UUID PESSOA = UUID.randomUUID();

    @Autowired
    private MonthlyGoalService metas;

    @Autowired
    private MonthlyGoalRepository repositorio;

    @BeforeEach
    void limpar() {
        repositorio.deleteAll();
    }

    @Test
    @DisplayName("definir o mesmo mes duas vezes substitui, nao duplica")
    void redefinirSubstitui() {
        metas.definir("2026-09", new BigDecimal("800"), PESSOA);
        metas.definir("2026-09", new BigDecimal("950"), PESSOA);

        assertThat(metas.listar()).hasSize(1);
        assertThat(metas.listar().get("2026-09")).isEqualByComparingTo("950");
    }

    @Test
    @DisplayName("meses diferentes convivem")
    void mesesDiferentes() {
        metas.definir("2026-08", new BigDecimal("700"), PESSOA);
        metas.definir("2026-09", new BigDecimal("800"), PESSOA);

        assertThat(metas.listar()).containsOnlyKeys("2026-08", "2026-09");
    }

    @Test
    @DisplayName("sem metas o mapa vem vazio, nao nulo")
    void semMetas() {
        assertThat(metas.listar()).isEmpty();
    }
}
