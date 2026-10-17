package com.piggu.identity.avisos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MensagemTest {

    @Test
    @DisplayName("cada tipo de aviso vira uma frase, na moeda da pessoa")
    void frases() {
        Mensagem mensagem = Mensagem.de(List.of(
                new Aviso("a", "ORCAMENTO_ATENCAO", "Mercado", new BigDecimal("850"), new BigDecimal("1000"),
                        new BigDecimal("85"), null),
                new Aviso("b", "ORCAMENTO_ESTOUROU", "Lazer", new BigDecimal("320"), new BigDecimal("300"),
                        new BigDecimal("107"), null),
                new Aviso("c", "CONTA_VENCENDO", "Luz", new BigDecimal("180"), null, null, LocalDate.of(2026, 10, 22)),
                new Aviso("d", "CONTA_VENCIDA", "Aluguel", new BigDecimal("2000"), null, null,
                        LocalDate.of(2026, 10, 10))), "BRL");

        assertThat(mensagem.titulo()).isEqualTo("Piggu: 4 avisos de hoje");
        assertThat(String.join("\n", mensagem.linhas()).replace(' ', ' '))
                .contains("Mercado ja usou 85% do limite (R$ 850,00 de R$ 1.000,00)")
                .contains("Lazer passou do limite: R$ 320,00 de R$ 300,00")
                .contains("Luz vence em 22/10: R$ 180,00")
                .contains("Aluguel venceu em 10/10 e nao foi paga: R$ 2.000,00");
        assertThat(mensagem.resumo()).endsWith("(e mais 3)");
        assertThat(mensagem.texto()).contains("Perfil > Avisos");
    }
}
