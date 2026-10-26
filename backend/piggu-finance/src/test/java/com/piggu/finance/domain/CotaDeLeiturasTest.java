package com.piggu.finance.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.common.security.Plano;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dez leituras gratis por mes e por familia; o mes seguinte comeca do zero. */
class CotaDeLeiturasTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("a 11a leitura do mes no gratuito e Premium; outra familia e o Premium nao sao afetados")
    void dezPorMes() {
        CotaDeLeituras cota = new CotaDeLeituras(jdbc, 10, emOutubro());
        CurrentUser casa = gratuita();
        for (int i = 1; i <= 10; i++) {
            assertThat(cota.reservar(casa)).isEqualTo(10 - i);
        }

        assertThatThrownBy(() -> cota.reservar(casa))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo(Plano.CODIGO_PREMIUM))
                .hasMessageContaining("10 notas grátis");
        assertThat(cota.uso(casa).usadas()).as("a recusada nao conta").isEqualTo(10);
        cota.reservar(gratuita());

        CurrentUser premium = new CurrentUser(UUID.randomUUID(), "p@x.test", PigguRole.TITULAR, Plano.PREMIUM, casa.familia());
        assertThat(cota.reservar(premium)).isNull();
        assertThat(cota.uso(premium).restantes()).isNull();
    }

    @Test
    @DisplayName("leitura que falhou volta para a cota")
    void devolver() {
        CotaDeLeituras cota = new CotaDeLeituras(jdbc, 1, emOutubro());
        CurrentUser casa = gratuita();
        cota.reservar(casa);
        cota.devolver(casa);

        assertThat(cota.reservar(casa)).isZero();
    }

    @Test
    @DisplayName("leituras ao mesmo tempo nao passam do limite")
    void aoMesmoTempo() throws Exception {
        CotaDeLeituras cota = new CotaDeLeituras(jdbc, 3, emOutubro());
        CurrentUser casa = gratuita();
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Boolean>> leituras = new ArrayList<>();
        try (var threads = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < 10; i++) {
                leituras.add(threads.submit(() -> {
                    largada.await();
                    try {
                        cota.reservar(casa);
                        return true;
                    } catch (BusinessException semCota) {
                        return false;
                    }
                }));
            }
            largada.countDown();
            int passaram = 0;
            for (Future<Boolean> leitura : leituras) {
                passaram += leitura.get() ? 1 : 0;
            }
            assertThat(passaram).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("o mes seguinte comeca do zero")
    void mesSeguinte() {
        CurrentUser casa = gratuita();
        CotaDeLeituras outubro = new CotaDeLeituras(jdbc, 1, emOutubro());
        outubro.reservar(casa);
        assertThatThrownBy(() -> outubro.reservar(casa)).isInstanceOf(BusinessException.class);

        CotaDeLeituras novembro = new CotaDeLeituras(jdbc, 1,
                Clock.fixed(Instant.parse("2026-11-01T03:30:00Z"), ZoneId.of("America/Sao_Paulo")));
        assertThat(novembro.uso(casa).restantes()).isEqualTo(1);
        novembro.reservar(casa);
    }

    private static CurrentUser gratuita() {
        return new CurrentUser(UUID.randomUUID(), "g@x.test", PigguRole.TITULAR, Plano.GRATUITO, UUID.randomUUID());
    }

    private static Clock emOutubro() {
        return Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    }
}
