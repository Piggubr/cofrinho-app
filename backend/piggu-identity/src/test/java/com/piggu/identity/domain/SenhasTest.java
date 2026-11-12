package com.piggu.identity.domain;

import com.piggu.common.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regras da senha, com o texto que vai para a tela. */
class SenhasTest {

    private final Senhas senhas = new Senhas();

    @Test
    @DisplayName("senha sem numero e senha igual ao e-mail sao fracas; letras e numeros passam")
    void regras() {
        assertThatThrownBy(() -> senhas.validar("sonheiumdia", "ana@piggu.test"))
                .isInstanceOf(BusinessException.class).hasMessage("Use letras e números na senha.");
        assertThatThrownBy(() -> senhas.validar(" Ana1@piggu.test ", "ana1@piggu.test"))
                .isInstanceOf(BusinessException.class).hasMessage("A senha não pode ser o seu e-mail.")
                .extracting("codigo").isEqualTo("SENHA_FRACA");
        assertThatCode(() -> senhas.validar("Fofura2026ok", "ana@piggu.test")).doesNotThrowAnyException();
    }
}
