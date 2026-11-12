package br.com.piggu.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FusoDaContaTest {
    @Test
    fun `o mes atual e o hoje seguem Brasilia, nao o fuso do aparelho`() {
        // 1º de novembro, 01:00 em Lisboa (UTC): em Brasília ainda é 31 de outubro, 22:00.
        val instante = Instant.parse("2026-11-01T01:00:00Z")
        val relogio = Clock.fixed(instante, FUSO_DA_CONTA)
        assertEquals("2026-10", relogio.mesAtual())
        assertEquals(LocalDate.of(2026, 10, 31), LocalDate.now(relogio))
        assertEquals("2026-11", Clock.fixed(instante, ZoneId.of("Europe/Lisbon")).mesAtual())
    }

    @Test
    fun `o relogio do app usa o fuso da conta`() {
        assertEquals(ZoneId.of("America/Sao_Paulo"), ModuloDoEscopo.relogio().zone)
    }
}
