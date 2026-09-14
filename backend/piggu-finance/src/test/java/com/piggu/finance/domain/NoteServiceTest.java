package com.piggu.finance.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.ForbiddenException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.finance.api.dto.ExpenseResponse;
import com.piggu.finance.api.dto.NoteRequest;
import com.piggu.finance.api.dto.NoteResponse;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Nota vinculada a gasto.
 *
 * <p>No Apps Script isso eram duas escritas em abas diferentes, sem transacao: uma
 * falha no meio deixava um gasto sem nota. O vinculo nos dois sentidos, criar e
 * apagar, e o que estes testes protegem.</p>
 */
class NoteServiceTest extends PostgresIntegrationTest {

    private static final CurrentUser BEATRIZ =
            new CurrentUser(UUID.randomUUID(), "beatriz@piggu.test", PigguRole.BEATRIZ);

    @Autowired
    private NoteService notas;

    @Autowired
    private ExpenseService gastos;

    @Autowired
    private NoteRepository repositorioDeNotas;

    @Autowired
    private ExpenseRepository repositorioDeGastos;

    @BeforeEach
    void limpar() {
        repositorioDeNotas.deleteAll();
        repositorioDeGastos.deleteAll();
    }

    @Test
    @DisplayName("nota sem valor nao cria gasto")
    void notaSemValorNaoCriaGasto() {
        NoteResponse nota = notas.criar(new NoteRequest(
                "Ligar para o dentista", "Marcar consulta", LocalDate.now(), null, null), BEATRIZ.email());

        assertThat(nota.gastoId()).isNull();
        assertThat(gastos.listar()).isEmpty();
    }

    @Test
    @DisplayName("nota com valor cria um gasto com origem Nota")
    void notaComValorCriaGasto() {
        NoteResponse nota = notas.criar(new NoteRequest(
                "Jantar de aniversario", "Reservar mesa",
                LocalDate.of(2026, 9, 20), new BigDecimal("75"), "Lazer"), BEATRIZ.email());

        assertThat(nota.gastoId()).isNotNull();

        ExpenseResponse gasto = gastos.listar().get(0);
        assertThat(gasto.id()).isEqualTo(nota.gastoId());
        assertThat(gasto.item()).isEqualTo("Jantar de aniversario");
        assertThat(gasto.valor()).isEqualByComparingTo("75");
        assertThat(gasto.categoria()).isEqualTo("Lazer");
        assertThat(gasto.origem()).isEqualTo("Nota");
        assertThat(gasto.data()).isEqualTo(LocalDate.of(2026, 9, 20));
    }

    @Test
    @DisplayName("apagar a nota apaga o gasto vinculado")
    void apagarNotaApagaGasto() {
        NoteResponse nota = notas.criar(new NoteRequest(
                "Cinema", "", LocalDate.now(), new BigDecimal("18"), "Lazer"), BEATRIZ.email());
        assertThat(gastos.listar()).hasSize(1);

        notas.excluir(nota.id(), BEATRIZ);

        assertThat(gastos.listar()).isEmpty();
        assertThat(notas.listar()).isEmpty();
    }

    @Test
    @DisplayName("apagar nota sem valor nao derruba nada alem dela")
    void apagarNotaSemValor() {
        gastos.listar();
        NoteResponse nota = notas.criar(new NoteRequest(
                "Lembrete solto", "", null, null, null), BEATRIZ.email());

        notas.excluir(nota.id(), BEATRIZ);

        assertThat(notas.listar()).isEmpty();
    }

    @Test
    @DisplayName("evento pago sem data e recusado")
    void eventoPagoExigeData() {
        assertThatThrownBy(() -> notas.criar(new NoteRequest(
                "Show", "", null, new BigDecimal("40"), "Lazer"), BEATRIZ.email()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("data do evento pago");
    }

    @Test
    @DisplayName("categoria desconhecida no evento pago cai em Outros")
    void categoriaDesconhecidaCaiEmOutros() {
        NoteResponse nota = notas.criar(new NoteRequest(
                "Curso", "", LocalDate.now(), new BigDecimal("10"), "CategoriaInventada"), BEATRIZ.email());

        assertThat(nota.categoria()).isEqualTo("Outros");
        assertThat(gastos.listar().get(0).categoria()).isEqualTo("Outros");
    }

    @Test
    @DisplayName("ninguem apaga nota de outra pessoa")
    void naoApagaNotaAlheia() {
        NoteResponse nota = notas.criar(new NoteRequest(
                "Particular", "", null, null, null), BEATRIZ.email());
        CurrentUser outra = new CurrentUser(UUID.randomUUID(), "familiar@piggu.test", PigguRole.FAMILIAR);

        assertThatThrownBy(() -> notas.excluir(nota.id(), outra))
                .isInstanceOf(ForbiddenException.class);
    }
}
