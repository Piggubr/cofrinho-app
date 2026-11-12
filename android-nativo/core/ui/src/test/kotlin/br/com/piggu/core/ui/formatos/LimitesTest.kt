package br.com.piggu.core.ui.formatos

import br.com.piggu.core.ui.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class LimitesTest {
    @Test
    fun `zero, vazio e negativo pedem a partir de 1 centavo`() {
        assertEquals(R.string.valor_minimo, problemaDoValor(null))
        assertEquals(R.string.valor_minimo, problemaDoValor(BigDecimal("0.00")))
        assertEquals(R.string.valor_minimo, problemaDoValor(BigDecimal("-5")))
        assertNull(problemaDoValor(BigDecimal("0.01")))
    }

    @Test
    fun `o teto do gasto e 1 milhao e o da receita 10 milhoes`() {
        assertNull(problemaDoValor(BigDecimal("1000000.00")))
        assertEquals(R.string.valor_maximo_gasto, problemaDoValor(BigDecimal("1000000.01")))
        assertNull(problemaDoValor(BigDecimal("10000000.00"), TETO_DA_RECEITA))
        assertEquals(R.string.valor_maximo_receita, problemaDoValor(BigDecimal("999999999.99"), TETO_DA_RECEITA))
    }

    @Test
    fun `data futura ou de mais de um ano pede confirmacao`() {
        val hoje = LocalDate.of(2026, 11, 11)
        assertFalse(dataPedeConfirmacao(hoje, hoje))
        assertFalse(dataPedeConfirmacao(LocalDate.of(2025, 11, 11), hoje))
        assertTrue(dataPedeConfirmacao(LocalDate.of(2025, 11, 10), hoje))
        assertTrue(dataPedeConfirmacao(hoje.plusDays(1), hoje))
        assertTrue(dataPedeConfirmacao(LocalDate.of(1900, 1, 1), hoje))
    }
}
