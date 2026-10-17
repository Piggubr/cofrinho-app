package com.piggu.rewards.domain;

import com.piggu.rewards.api.dto.CoinAdjustRequest;
import com.piggu.rewards.api.dto.PrizeRequest;
import com.piggu.rewards.api.dto.PrizeResponse;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;

/**
 * Gasto duplo no resgate de premios.
 *
 * <p>Este teste e a razao de RewardsService pedir isolamento SERIALIZABLE. Sem ele,
 * duas chamadas simultaneas leriam o mesmo saldo, ambas passariam na verificacao e
 * as duas debitariam: o saldo terminaria negativo e dois premios teriam saido pelo
 * preco de um. O Apps Script evitava isso com LockService, que serializava o script
 * inteiro; aqui a garantia e do banco e vale so para esta operacao.</p>
 *
 * <p>Nao importa qual das duas chamadas vence, nem se a perdedora falha por saldo
 * insuficiente ou por conflito de serializacao. O que precisa valer sempre e:
 * exatamente um resgate gravado e saldo final correto.</p>
 */
class ResgateConcorrenteTest extends PostgresIntegrationTest {

    private static final UUID ADMIN = UUID.nameUUIDFromBytes("admin@piggu.test".getBytes());

    @Autowired
    private RewardsService recompensas;

    @Autowired
    private CoinEntryRepository lancamentos;

    @Autowired
    private PrizeRepository premios;

    @Autowired
    private RedemptionRepository resgates;

    @BeforeEach
    void limpar() {
        resgates.deleteAll();
        premios.deleteAll();
        lancamentos.deleteAll();
    }

    @Test
    @DisplayName("dois resgates ao mesmo tempo com saldo para um so: apenas um passa")
    void naoPermiteGastarDuasVezes() throws Exception {
        recompensas.ajustar(new CoinAdjustRequest(60, "Credito"), ADMIN);
        PrizeResponse premio = recompensas.criarPremio(new PrizeRequest("Cinema", "", 60, true), ADMIN);

        AtomicInteger sucessos = new AtomicInteger();
        AtomicInteger falhas = new AtomicInteger();
        CyclicBarrier largada = new CyclicBarrier(2);

        Callable<Void> tentarResgatar = () -> {
            largada.await(10, TimeUnit.SECONDS);
            try {
                recompensas.resgatar(premio.id(), UUID.randomUUID());
                sucessos.incrementAndGet();
            } catch (RuntimeException erro) {
                falhas.incrementAndGet();
            }
            return null;
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Void>> corridas = executor.invokeAll(List.of(tentarResgatar, tentarResgatar));
            for (Future<Void> corrida : corridas) {
                corrida.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(sucessos.get()).as("resgates aceitos").isEqualTo(1);
        assertThat(falhas.get()).as("resgates recusados").isEqualTo(1);
        assertThat(recompensas.saldo().saldo()).as("saldo final").isZero();
        assertThat(recompensas.listarResgates()).as("resgates gravados").hasSize(1);
    }

    @Test
    @DisplayName("varios debitos simultaneos nunca derrubam o saldo abaixo de zero")
    void debitosSimultaneosNaoFicamNegativos() throws Exception {
        recompensas.ajustar(new CoinAdjustRequest(100, "Credito"), ADMIN);

        int tentativas = 6;
        CyclicBarrier largada = new CyclicBarrier(tentativas);
        AtomicInteger aceitos = new AtomicInteger();

        Callable<Void> debitar = () -> {
            largada.await(10, TimeUnit.SECONDS);
            try {
                recompensas.ajustar(new CoinAdjustRequest(-30, "Debito concorrente"), ADMIN);
                aceitos.incrementAndGet();
            } catch (RuntimeException erro) {
                // Recusado por saldo ou por conflito de serializacao: os dois sao aceitaveis.
            }
            return null;
        };

        ExecutorService executor = Executors.newFixedThreadPool(tentativas);
        try {
            List<Future<Void>> corridas = executor.invokeAll(
                    java.util.Collections.nCopies(tentativas, debitar));
            for (Future<Void> corrida : corridas) {
                corrida.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        int saldoFinal = recompensas.saldo().saldo();
        assertThat(saldoFinal).as("saldo nunca negativo").isNotNegative();
        assertThat(saldoFinal).as("saldo confere com os debitos aceitos")
                .isEqualTo(100 - (aceitos.get() * 30));
        assertThat(aceitos.get()).as("no maximo tres debitos de 30 cabem em 100").isLessThanOrEqualTo(3);
    }
}
