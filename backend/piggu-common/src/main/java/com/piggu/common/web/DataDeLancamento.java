package com.piggu.common.web;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;

/**
 * Data de gasto, receita, valor a receber ou deposito: de 01/01/2000 ate hoje (Brasilia) mais
 * dois anos (D-189). Antes, 01/01/1900 e 31/12/2099 entravam sem aviso (BUG-023). Nula passa:
 * quem exige a data e o {@code @NotNull} do campo.
 */
@Documented
@Constraint(validatedBy = DataDeLancamento.Validador.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataDeLancamento {

    LocalDate PRIMEIRA = LocalDate.of(2000, 1, 1);

    String message() default "Confira a data.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** O teto anda com o calendario: a mensagem diz o ano de hoje. */
    class Validador implements ConstraintValidator<DataDeLancamento, LocalDate> {

        @Override
        public boolean isValid(LocalDate data, ConstraintValidatorContext contexto) {
            LocalDate ultima = LocalDate.now(Meses.BRASILIA).plusYears(2);
            if (data == null || !data.isBefore(PRIMEIRA) && !data.isAfter(ultima)) {
                return true;
            }
            contexto.disableDefaultConstraintViolation();
            contexto.buildConstraintViolationWithTemplate(
                    "Confira a data: use uma data entre 2000 e " + ultima.getYear() + ".").addConstraintViolation();
            return false;
        }
    }
}
