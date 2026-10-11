package com.piggu.banking.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Do numero da conta que vem da Pluggy, so os 4 ultimos caracteres uteis (S7). */
class UltimosDigitosTest {

    @ParameterizedTest(name = "\"{0}\" vira \"{1}\"")
    @CsvSource(value = {
            "00012345-6 | 3456",
            "1234       | 1234",
            "12         | 12",
            "4111 1111 1111 1234 | 1234",
            "AB.12-3X   | 123X",
            "''         | ''",
    }, delimiter = '|')
    void cortaParaOsUltimosQuatro(String numero, String esperado) {
        assertThat(BankAccount.ultimosQuatro(numero)).isEqualTo(esperado);
    }

    @org.junit.jupiter.api.Test
    void nuloViraVazio() {
        assertThat(BankAccount.ultimosQuatro(null)).isEmpty();
    }
}
