package com.piggu.common.web;

import com.piggu.common.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoedasTest {

    @Test
    @DisplayName("aceita codigo ISO 4217 em qualquer caixa")
    void aceitaIso() {
        assertThat(Moedas.validar("brl")).isEqualTo("BRL");
        assertThat(Moedas.validar(" JPY ")).isEqualTo("JPY");
    }

    @Test
    @DisplayName("recusa codigo inexistente, formato errado e vazio")
    void recusaInvalido() {
        assertThatThrownBy(() -> Moedas.validar("XYZ")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> Moedas.validar("EU")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> Moedas.validar("EUR/BRL")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> Moedas.validar(null)).isInstanceOf(BusinessException.class);
    }
}
