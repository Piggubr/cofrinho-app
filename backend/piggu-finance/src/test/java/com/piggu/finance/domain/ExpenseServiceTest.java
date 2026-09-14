package com.piggu.finance.domain;

import com.piggu.common.error.NotFoundException;
import com.piggu.finance.api.dto.ExpenseItemRequest;
import com.piggu.finance.api.dto.ExpenseResponse;
import com.piggu.finance.api.dto.SaveExpensesRequest;
import com.piggu.finance.api.dto.UpdateExpenseRequest;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lancamento, edicao e exclusao de gastos. */
class ExpenseServiceTest extends PostgresIntegrationTest {

    private static final String EMAIL = "beatriz@piggu.test";

    @Autowired
    private ExpenseService gastos;

    @Autowired
    private ExpenseRepository repositorio;

    @BeforeEach
    void limpar() {
        repositorio.deleteAll();
    }

    @Test
    @DisplayName("itens do mesmo lancamento compartilham o recibo")
    void itensCompartilhamRecibo() {
        List<ExpenseResponse> salvos = gastos.salvar(new SaveExpensesRequest(
                LocalDate.of(2026, 9, 10), "Continente", null, "Foto",
                List.of(
                        new ExpenseItemRequest("Leite", "Alimentação", new BigDecimal("1.29"), "Variavel"),
                        new ExpenseItemRequest("Pao", "Alimentação", new BigDecimal("0.90"), null))
        ), EMAIL);

        assertThat(salvos).hasSize(2);
        assertThat(salvos.get(0).reciboId()).isEqualTo(salvos.get(1).reciboId());
        assertThat(salvos).allSatisfy(gasto -> {
            assertThat(gasto.estabelecimento()).isEqualTo("Continente");
            assertThat(gasto.origem()).isEqualTo("Foto");
            assertThat(gasto.usuario()).isEqualTo(EMAIL);
        });
    }

    @Test
    @DisplayName("campos ausentes caem nos padroes do Apps Script")
    void padroesQuandoAusente() {
        ExpenseResponse gasto = gastos.salvar(new SaveExpensesRequest(
                LocalDate.now(), null, null, null,
                List.of(new ExpenseItemRequest("Item", null, BigDecimal.ONE, null))
        ), EMAIL).get(0);

        assertThat(gasto.estabelecimento()).isEmpty();
        assertThat(gasto.origem()).isEqualTo("Manual");
        assertThat(gasto.tipo()).isEqualTo("Variavel");
        assertThat(gasto.categoria()).isEqualTo("Outros");
    }

    @Test
    @DisplayName("recibo informado e preservado, para reagrupar itens conferidos na tela")
    void reciboInformadoEPreservado() {
        UUID recibo = UUID.randomUUID();

        ExpenseResponse gasto = gastos.salvar(new SaveExpensesRequest(
                LocalDate.now(), "Loja", recibo, "Foto",
                List.of(new ExpenseItemRequest("Item", "Lazer", BigDecimal.TEN, null))
        ), EMAIL).get(0);

        assertThat(gasto.reciboId()).isEqualTo(recibo);
    }

    @Test
    @DisplayName("edicao muda nome, categoria e valor, e preserva o resto")
    void edicaoPreservaOResto() {
        ExpenseResponse original = gastos.salvar(new SaveExpensesRequest(
                LocalDate.of(2026, 9, 10), "Continente", null, "Foto",
                List.of(new ExpenseItemRequest("Leite", "Alimentação", new BigDecimal("1.29"), "Variavel"))
        ), EMAIL).get(0);

        ExpenseResponse editado = gastos.atualizar(original.id(),
                new UpdateExpenseRequest("Leite meio gordo", "Lazer", new BigDecimal("2.50")));

        assertThat(editado.item()).isEqualTo("Leite meio gordo");
        assertThat(editado.categoria()).isEqualTo("Lazer");
        assertThat(editado.valor()).isEqualByComparingTo("2.50");
        assertThat(editado.estabelecimento()).isEqualTo("Continente");
        assertThat(editado.data()).isEqualTo(original.data());
        assertThat(editado.reciboId()).isEqualTo(original.reciboId());
    }

    @Test
    @DisplayName("filtro por mes respeita a virada do mes")
    void filtraPorMes() {
        lancar(LocalDate.of(2026, 8, 31));
        lancar(LocalDate.of(2026, 9, 1));
        lancar(LocalDate.of(2026, 9, 30));
        lancar(LocalDate.of(2026, 10, 1));

        assertThat(gastos.listarDoMes("2026-09")).hasSize(2);
    }

    @Test
    @DisplayName("gasto inexistente da erro claro, nao falha generica")
    void gastoInexistente() {
        UUID ausente = UUID.randomUUID();

        assertThatThrownBy(() -> gastos.excluir(ausente))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Gasto nao encontrado.");
    }

    @Test
    @DisplayName("exclusao silenciosa ignora id inexistente ou nulo")
    void exclusaoSilenciosa() {
        gastos.excluirSeExistir(null);
        gastos.excluirSeExistir(UUID.randomUUID());
    }

    private void lancar(LocalDate data) {
        gastos.salvar(new SaveExpensesRequest(data, "Loja", null, "Manual",
                List.of(new ExpenseItemRequest("Item " + data, "Lazer", BigDecimal.ONE, null))), EMAIL);
    }
}
