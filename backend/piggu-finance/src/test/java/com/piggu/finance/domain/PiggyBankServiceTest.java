package com.piggu.finance.domain;

import com.piggu.common.error.ForbiddenException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.finance.api.dto.DepositRequest;
import com.piggu.finance.api.dto.DepositResponse;
import com.piggu.finance.api.dto.ExpenseItemRequest;
import com.piggu.finance.api.dto.PiggyBankResponse;
import com.piggu.finance.api.dto.SaveExpensesRequest;
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

/**
 * Saldo do cofrinho.
 *
 * <p>A regra herdada do Apps Script nao e obvia e por isso e a mais facil de quebrar
 * sem querer: o saldo desconta apenas os gastos <em>registrados depois</em> do
 * primeiro deposito. Gastos anteriores ao cofrinho nao pertencem a ele.</p>
 */
class PiggyBankServiceTest extends PostgresIntegrationTest {

    private static final CurrentUser TITULAR =
            new CurrentUser(UUID.randomUUID(), "titular@piggu.test", PigguRole.TITULAR);
    private static final CurrentUser MEMBRO =
            new CurrentUser(UUID.randomUUID(), "membro@piggu.test", PigguRole.MEMBRO);

    @Autowired
    private PiggyBankService cofrinho;

    @Autowired
    private ExpenseService gastos;

    @Autowired
    private PiggyDepositRepository depositos;

    @Autowired
    private ExpenseRepository repositorioDeGastos;

    @BeforeEach
    void limpar() {
        depositos.deleteAll();
        repositorioDeGastos.deleteAll();
    }

    @Test
    @DisplayName("gasto anterior ao primeiro deposito nao entra na conta")
    void gastoAnteriorNaoDesconta() {
        lancarGasto("Cafe antigo", "20");
        cofrinho.depositar(new DepositRequest(LocalDate.now(), new BigDecimal("100")), TITULAR.id());

        PiggyBankResponse resposta = cofrinho.consultar(TITULAR);

        assertThat(resposta.totalDepositos()).isEqualByComparingTo("100");
        assertThat(resposta.totalGastos()).isEqualByComparingTo("0");
        assertThat(resposta.saldo()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("gasto posterior ao primeiro deposito desconta do saldo")
    void gastoPosteriorDesconta() {
        cofrinho.depositar(new DepositRequest(LocalDate.now(), new BigDecimal("100")), TITULAR.id());
        lancarGasto("Cafe novo", "30");

        PiggyBankResponse resposta = cofrinho.consultar(TITULAR);

        assertThat(resposta.totalGastos()).isEqualByComparingTo("30");
        assertThat(resposta.saldo()).isEqualByComparingTo("70");
    }

    @Test
    @DisplayName("saldo soma depositos de todas as pessoas")
    void somaDepositosDeTodos() {
        cofrinho.depositar(new DepositRequest(LocalDate.now(), new BigDecimal("100")), MEMBRO.id());
        cofrinho.depositar(new DepositRequest(LocalDate.now(), new BigDecimal("50")), TITULAR.id());

        assertThat(cofrinho.consultar(TITULAR).totalDepositos()).isEqualByComparingTo("150");
    }

    @Test
    @DisplayName("perfil membro ve so os proprios depositos, sem desconto de gastos")
    void membroVeApenasOProprio() {
        cofrinho.depositar(new DepositRequest(LocalDate.now(), new BigDecimal("100")), MEMBRO.id());
        cofrinho.depositar(new DepositRequest(LocalDate.now(), new BigDecimal("50")), TITULAR.id());
        lancarGasto("Mercado", "40");

        PiggyBankResponse resposta = cofrinho.consultar(MEMBRO);

        assertThat(resposta.depositos()).hasSize(1);
        assertThat(resposta.totalDepositos()).isEqualByComparingTo("100");
        assertThat(resposta.totalGastos()).isEqualByComparingTo("0");
        assertThat(resposta.saldo()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("cofrinho vazio responde zero em vez de falhar")
    void cofrinhoVazio() {
        PiggyBankResponse resposta = cofrinho.consultar(TITULAR);

        assertThat(resposta.depositos()).isEmpty();
        assertThat(resposta.saldo()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("deposito sem data usa o dia de hoje")
    void depositoSemDataUsaHoje() {
        DepositResponse deposito =
                cofrinho.depositar(new DepositRequest(null, new BigDecimal("10")), TITULAR.id());

        assertThat(deposito.data()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("ninguem apaga deposito de outra pessoa")
    void naoApagaDepositoAlheio() {
        DepositResponse alheio =
                cofrinho.depositar(new DepositRequest(LocalDate.now(), new BigDecimal("10")), TITULAR.id());

        assertThatThrownBy(() -> cofrinho.excluir(alheio.id(), MEMBRO))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("nao pode apagar");
    }

    @Test
    @DisplayName("administrador apaga deposito de qualquer pessoa")
    void adminApagaQualquerDeposito() {
        DepositResponse deposito =
                cofrinho.depositar(new DepositRequest(LocalDate.now(), new BigDecimal("10")), TITULAR.id());
        CurrentUser admin = new CurrentUser(UUID.randomUUID(), "admin@piggu.test", PigguRole.ADMIN);

        cofrinho.excluir(deposito.id(), admin);

        assertThat(cofrinho.consultar(admin).depositos()).isEmpty();
    }

    private void lancarGasto(String item, String valor) {
        gastos.salvar(new SaveExpensesRequest(
                LocalDate.now(), "Loja", null, "Manual",
                List.of(new ExpenseItemRequest(item, "Lazer", new BigDecimal(valor), "Variavel"))
        ), TITULAR.id());
    }
}
