package br.com.piggu.core.ui.formatos

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Os casos de campo-de-valor.spec.ts e de FormatosTests.swift: os três leem igual (D-076).
class ValorTest {

    private fun le(texto: String?, esperado: String) =
        assertEquals("lerValor(\"$texto\")", BigDecimal(esperado).setScale(2), lerValor(texto))

    private fun recusa(texto: String?) = assertNull("lerValor(\"$texto\")", lerValor(texto))

    @Test
    fun `aceita virgula e ponto como decimal`() {
        le("12,50", "12.5")
        le("12.50", "12.5")
        le("0,99", "0.99")
        le(",5", "0.5")
        le("7", "7")
    }

    @Test
    fun `entende o milhar nos dois padroes`() {
        le("1.234,56", "1234.56")
        le("1,234.56", "1234.56")
        le("1.500", "1500")
        le("1.234.567", "1234567")
        le("1.234.567,89", "1234567.89")
    }

    @Test
    fun `ignora simbolo e espacos, e o separador ainda sem digito depois`() {
        le("R$ 12,50", "12.5")
        le("12,", "12")
        le("1.5", "1.5")
    }

    @Test
    fun `0_500 nao e milhar, agrupamento torto e tres casas sao nulos`() {
        recusa("0.500")
        le("0.50", "0.5")
        recusa("1.2.3")
        recusa("1,234,567")
        recusa("1,005")
        recusa("10,005")
        recusa("12,345")
        recusa("-5")
        recusa("12,5a")
        recusa(",")
        recusa("R$")
    }

    @Test
    fun `sem numero, devolve nulo`() {
        recusa("")
        recusa(" ")
        recusa("abc")
        recusa(null)
    }

    @Test
    fun `escreve no padrao brasileiro`() {
        assertEquals("12,50", escreverValor(BigDecimal("12.5")))
        assertEquals("1234,00", escreverValor(BigDecimal("1234")))
        assertEquals("", escreverValor(null))
    }

    @Test
    fun `le valor com virgula ou ponto como o iOS`() {
        le("12,50", "12.5")
        le("12,5", "12.5")
        le("0,99", "0.99")
        le(",50", "0.5")
        le("42", "42")
        le("1.234,56", "1234.56")
        le("1.234", "1234")
        le("1.234.567,89", "1234567.89")
        le("12.5", "12.5")
        le("12.50", "12.5")
        le("1,234.56", "1234.56")
        le("R$ 15,90", "15.9")
        le(" 7,00 ", "7")
    }

    @Test
    fun `recusa valor invalido como o iOS`() {
        listOf("", "  ", "abc", "12,345", "1,2,3", "12,5a", "-5", "1.2.3", "R$", ",", "0.500").forEach(::recusa)
    }

    @Test
    fun `le o valor que a moeda escreveu, com espaco nao quebravel`() {
        le("R$ 1.234,50", "1234.5")
    }
}
