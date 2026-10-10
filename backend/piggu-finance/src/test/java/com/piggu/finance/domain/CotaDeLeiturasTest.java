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
import java.util.UUID;

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
            cota.exigirDisponivel(casa);
            assertThat(cota.registrar(casa)).isEqualTo(10 - i);
        }

        assertThatThrownBy(() -> cota.exigirDisponivel(casa))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo(Plano.CODIGO_PREMIUM))
                .hasMessageContaining("10 notas grátis");
        cota.exigirDisponivel(gratuita());

        CurrentUser premium = new CurrentUser(UUID.randomUUID(), "p@x.test", PigguRole.TITULAR, Plano.PREMIUM, casa.familia());
        cota.exigirDisponivel(premium);
        assertThat(cota.uso(premium).restantes()).isNull();
    }

    @Test
    @DisplayName("o mes seguinte comeca do zero")
    void mesSeguinte() {
        CurrentUser casa = gratuita();
        CotaDeLeituras outubro = new CotaDeLeituras(jdbc, 1, emOutubro());
        outubro.registrar(casa);
        assertThatThrownBy(() -> outubro.exigirDisponivel(casa)).isInstanceOf(BusinessException.class);

        CotaDeLeituras novembro = new CotaDeLeituras(jdbc, 1,
                Clock.fixed(Instant.parse("2026-11-01T03:30:00Z"), ZoneId.of("America/Sao_Paulo")));
        novembro.exigirDisponivel(casa);
        assertThat(novembro.uso(casa).restantes()).isEqualTo(1);
    }

    private static CurrentUser gratuita() {
        return new CurrentUser(UUID.randomUUID(), "g@x.test", PigguRole.TITULAR, Plano.GRATUITO, UUID.randomUUID());
    }

    private static Clock emOutubro() {
        return Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    }
}
