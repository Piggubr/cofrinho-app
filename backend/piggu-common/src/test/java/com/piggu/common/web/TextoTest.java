package com.piggu.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Higienizacao de texto.
 *
 * <p>Estas regras vieram do Apps Script, onde cada gravacao repetia
 * String(valor).trim().slice(0, N). Se elas mudarem, dados passam a ser
 * truncados ou aceitos de forma diferente em todo o sistema de uma vez.</p>
 */
class TextoTest {

    @Test
    @DisplayName("nulo vira string vazia, nunca NullPointerException")
    void nuloViraVazio() {
        assertThat(Texto.limitar(null, 10)).isEmpty();
        assertThat(Texto.email(null)).isEmpty();
        assertThat(Texto.espacoUnico(null)).isEmpty();
        assertThat(Texto.semAcento(null)).isEmpty();
    }

    @Test
    @DisplayName("corta no limite da coluna do banco")
    void cortaNoLimite() {
        assertThat(Texto.limitar("abcdefghij", 4)).isEqualTo("abcd");
        assertThat(Texto.limitar("abc", 10)).isEqualTo("abc");
    }

    @Test
    @DisplayName("apara antes de cortar, para nao gastar o limite com espaco")
    void aparaAntesDeCortar() {
        assertThat(Texto.limitar("   leite   ", 5)).isEqualTo("leite");
    }

    @Test
    @DisplayName("valor que sobra vazio cai no padrao")
    void usaPadraoQuandoVazio() {
        assertThat(Texto.limitarOuPadrao("   ", 10, "Outros")).isEqualTo("Outros");
        assertThat(Texto.limitarOuPadrao(null, 10, "Manual")).isEqualTo("Manual");
        assertThat(Texto.limitarOuPadrao("Foto", 10, "Manual")).isEqualTo("Foto");
    }

    @Test
    @DisplayName("e-mail vira minusculo e sem espacos, que e a forma usada como chave")
    void emailNormalizado() {
        assertThat(Texto.email("  Titular@Gmail.COM ")).isEqualTo("titular@gmail.com");
    }

    @Test
    @DisplayName("espacos repetidos viram um so")
    void colapsaEspacos() {
        assertThat(Texto.espacoUnico("leite    meio   gordo")).isEqualTo("leite meio gordo");
        assertThat(Texto.espacoUnico("com\ttabulacao")).isEqualTo("com tabulacao");
    }

    @Test
    @DisplayName("remove acento mantendo a letra base")
    void removeAcento() {
        assertThat(Texto.semAcento("Alimentação")).isEqualTo("Alimentacao");
        assertThat(Texto.semAcento("Farmácia/Saúde")).isEqualTo("Farmacia/Saude");
    }

    @Test
    @DisplayName("vazio considera nulo e espaco em branco")
    void detectaVazio() {
        assertThat(Texto.vazio(null)).isTrue();
        assertThat(Texto.vazio("   ")).isTrue();
        assertThat(Texto.vazio("x")).isFalse();
    }
}
