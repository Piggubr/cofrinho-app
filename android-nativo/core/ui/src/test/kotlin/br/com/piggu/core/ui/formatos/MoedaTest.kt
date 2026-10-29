package br.com.piggu.core.ui.formatos

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// moeda.spec.ts e FormatosTests.swift. O pt-BR separa o símbolo do número com espaço não quebrável.
class MoedaTest {

    private fun moeda(valor: String?, codigo: String?) =
        formatarMoeda(valor?.let(::BigDecimal), codigo).replace(' ', ' ')

    @Test
    fun `sem sessao usa euro, a moeda historica do app`() {
        assertTrue(moeda("1.29", null).contains("1,29"))
        assertTrue(moeda("1.29", null).contains("€"))
    }

    @Test
    fun `segue a moeda escolhida`() {
        assertEquals("R$ 1.234,50", moeda("1234.5", "BRL"))
        assertEquals("US$", simboloDaMoeda("USD"))
        assertEquals("R$", simboloDaMoeda("BRL"))
    }

    @Test
    fun `trata valor ausente como zero`() {
        assertTrue(moeda(null, "BRL").contains("0,00"))
    }

    @Test
    fun `moeda sem simbolo proprio mostra o codigo`() {
        assertEquals("CHF", simboloDaMoeda("CHF"))
    }

    @Test
    fun `codigo desconhecido nao quebra a tela`() {
        assertEquals("XYZ 1.234,50", moeda("1234.5", "XYZ"))
        assertEquals("XYZ", simboloDaMoeda("XYZ"))
    }
}

class CotacaoTest {
    @org.junit.Test
    fun `abaixo de 1 mostra ate quatro casas e acima, duas`() {
        org.junit.Assert.assertEquals("1 BRL = US$ 0,1786", formatarCotacao("BRL", "USD", java.math.BigDecimal("0.17861")))
        org.junit.Assert.assertEquals("1 USD = R$ 5,60", formatarCotacao("USD", "BRL", java.math.BigDecimal("5.6")))
    }
}
