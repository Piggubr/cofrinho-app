package com.piggu.identity.domain;

import com.piggu.common.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaisesTest {

    @Test
    @DisplayName("pais e codigo ISO de duas letras; o que nao e pais e recusado")
    void paises() {
        assertThat(Paises.pais(" pt ")).isEqualTo("PT");
        assertThat(Paises.pais("BR")).isEqualTo(Paises.BRASIL);
        assertThatThrownBy(() -> Paises.pais("XX"))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("PAIS_INVALIDO"));
        assertThatThrownBy(() -> Paises.pais(null)).isInstanceOf(BusinessException.class);
    }
}
