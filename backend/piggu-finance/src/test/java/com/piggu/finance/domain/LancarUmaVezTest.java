package com.piggu.finance.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.security.FamiliaAtual;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dois toques ao mesmo tempo em "Paga" ou "Recebi" lancam uma vez so. */
class LancarUmaVezTest extends PostgresIntegrationTest {

    @Autowired
    private ContasFixasService contas;

    @Autowired
    private AReceberService aReceber;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID familia = UUID.randomUUID();
    private final UUID dona = UUID.randomUUID();

    @Test
    @DisplayName("conta fixa paga duas vezes ao mesmo tempo lanca um gasto so; a segunda e JA_PAGA")
    void contaFixa() throws Exception {
        UUID id = FamiliaAtual.como(familia, () -> contas.criar("Luz", "Outros", BigDecimal.TEN, 5, false, dona,
                false).getId());

        aoMesmoTempo(() -> contas.pagar(id, YearMonth.of(2026, 10), dona), "JA_PAGA");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses WHERE household_id = ?", Integer.class, familia))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("\"Recebi\" duas vezes ao mesmo tempo lanca uma receita so; a segunda e JA_RECEBIDO")
    void aReceber() throws Exception {
        UUID id = FamiliaAtual.como(familia, () -> aReceber.criar("Joao", "", BigDecimal.TEN,
                LocalDate.of(2026, 9, 1), dona).getId());

        aoMesmoTempo(() -> aReceber.receber(id, dona), "JA_RECEBIDO");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM incomes WHERE household_id = ?", Integer.class, familia))
                .isEqualTo(1);
    }

    /** A primeira fica com a transacao aberta enquanto a segunda confere e tenta lancar. */
    private void aoMesmoTempo(Runnable lancar, String codigo) throws Exception {
        CountDownLatch primeiraLancou = new CountDownLatch(1);
        CountDownLatch segundaTerminou = new CountDownLatch(1);
        try (var threads = Executors.newFixedThreadPool(2)) {
            Future<?> primeira = threads.submit(() -> FamiliaAtual.como(familia, () ->
                    transacao.executeWithoutResult(status -> {
                        lancar.run();
                        primeiraLancou.countDown();
                        try {
                            // Com a trava, a segunda fica esperando a primeira terminar.
                            segundaTerminou.await(2, TimeUnit.SECONDS);
                        } catch (InterruptedException interrompida) {
                            Thread.currentThread().interrupt();
                        }
                    })));
            Future<?> segunda = threads.submit(() -> {
                try {
                    primeiraLancou.await();
                    FamiliaAtual.como(familia, lancar);
                } finally {
                    segundaTerminou.countDown();
                }
                return null;
            });
            primeira.get();
            assertThatThrownBy(segunda::get).cause()
                    .isInstanceOfSatisfying(BusinessException.class, erro -> assertThat(erro.getCodigo()).isEqualTo(codigo));
        }
    }
}
