package com.piggu.common.dados;

import static com.piggu.common.dados.Consentimentos.VERSAO_ANTERIOR;
import static com.piggu.common.dados.Consentimentos.VERSAO_ANTERIOR_VALE_ATE;
import static com.piggu.common.dados.Consentimentos.VERSAO_DO_AVISO;
import static com.piggu.common.dados.Consentimentos.versaoAceita;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VersaoDoAvisoTest {

    @Test
    @DisplayName("a versao vigente vale sempre")
    void vigente() {
        assertThat(versaoAceita(VERSAO_DO_AVISO, LocalDate.of(2030, 1, 1))).isTrue();
    }

    @Test
    @DisplayName("a anterior vale ate o ultimo dia da transicao, para o app instalado nao quebrar (D-180)")
    void anteriorNaTransicao() {
        assertThat(versaoAceita(VERSAO_ANTERIOR, VERSAO_ANTERIOR_VALE_ATE)).isTrue();
        assertThat(versaoAceita(VERSAO_ANTERIOR, VERSAO_ANTERIOR_VALE_ATE.plusDays(1))).isFalse();
    }

    @Test
    @DisplayName("versao mais velha, inventada ou ausente e recusada")
    void outras() {
        LocalDate naTransicao = VERSAO_ANTERIOR_VALE_ATE.minusDays(1);
        assertThat(versaoAceita("2026-09", naTransicao)).isFalse();
        assertThat(versaoAceita("2099-01-01", naTransicao)).isFalse();
        assertThat(versaoAceita(null, naTransicao)).isFalse();
    }
}
