package com.piggu.identity.domain;

import com.piggu.common.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TentativasDeCpfTest {

    @Test
    @DisplayName("tres recusas de CPF de outra conta no dia param as tentativas; outra conta segue podendo (D-160)")
    void paraDepoisDeTres() {
        Instant agora = Instant.parse("2026-11-01T12:00:00Z");
        UUID conta = UUID.randomUUID();
        TentativasDeCpf hoje = new TentativasDeCpf(null, Clock.fixed(agora, ZoneOffset.UTC));
        for (int i = 0; i < TentativasDeCpf.MAXIMO; i++) {
            hoje.exigirTentativa(conta);
            hoje.recusado(conta);
        }
        assertThatThrownBy(() -> hoje.exigirTentativa(conta))
                .isInstanceOf(BusinessException.class).extracting("codigo").isEqualTo("CPF_TENTATIVAS");
        assertThatCode(() -> hoje.exigirTentativa(UUID.randomUUID())).doesNotThrowAnyException();
    }
}
