package com.piggu.identity.domain;

import com.piggu.common.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Digitos verificadores do CPF e o que nunca aparece inteiro. */
class CpfTest {

    @Test
    @DisplayName("CPF valido com ou sem pontuacao vira so os digitos")
    void valido() {
        assertThat(Cpf.de("529.982.247-25").numero()).isEqualTo("52998224725");
        assertThat(Cpf.de("52998224725").numero()).isEqualTo("52998224725");
        assertThat(Cpf.de(" 111.444.777-35 ").numero()).isEqualTo("11144477735");
    }

    @Test
    @DisplayName("digito verificador errado, numeros repetidos e tamanho errado sao recusados")
    void invalido() {
        for (String errado : new String[]{"529.982.247-24", "111.111.111-11", "000.000.000-00", "1234567890",
                "123456789012", "abc.def.ghi-jk", ""}) {
            assertThatThrownBy(() -> Cpf.de(errado)).as(errado).isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> Cpf.de(null)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("toString e mascarado: o numero inteiro nunca vai para log")
    void mascarado() {
        Cpf cpf = Cpf.de("52998224725");
        assertThat(cpf.toString()).isEqualTo("***.982.247-**");
        assertThat(cpf.formatado()).isEqualTo("529.982.247-25");
    }
}
