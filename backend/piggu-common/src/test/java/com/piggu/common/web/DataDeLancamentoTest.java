package com.piggu.common.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Faixa de datas de lancamento (D-189): de 01/01/2000 ate hoje em Brasilia mais 2 anos. */
class DataDeLancamentoTest {

    private final Validator validador = Validation.buildDefaultValidatorFactory().getValidator();

    record Pedido(@DataDeLancamento LocalDate data) {
    }

    private Set<ConstraintViolation<Pedido>> validar(LocalDate data) {
        return validador.validate(new Pedido(data));
    }

    @Test
    @DisplayName("os dois extremos entram; um dia antes ou depois sai")
    void extremos() {
        LocalDate ultima = LocalDate.now(Meses.BRASILIA).plusYears(2);
        assertThat(validar(DataDeLancamento.PRIMEIRA)).isEmpty();
        assertThat(validar(ultima)).isEmpty();
        assertThat(validar(LocalDate.of(1999, 12, 31))).hasSize(1);
        assertThat(validar(ultima.plusDays(1))).hasSize(1);
    }

    @Test
    @DisplayName("a mensagem diz o ano do teto e fica no campo data")
    void mensagemComOAno() {
        int teto = LocalDate.now(Meses.BRASILIA).plusYears(2).getYear();
        ConstraintViolation<Pedido> erro = validar(LocalDate.of(1900, 1, 1)).iterator().next();

        assertThat(erro.getMessage()).isEqualTo("Confira a data: use uma data entre 2000 e " + teto + ".");
        assertThat(erro.getPropertyPath()).hasToString("data");
    }

    @Test
    @DisplayName("data nula passa: quem exige a data e o @NotNull")
    void nulaPassa() {
        assertThat(validar(null)).isEmpty();
    }
}
