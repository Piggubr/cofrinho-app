package br.com.piggu.core.ui.formatos

import org.junit.Assert.assertEquals
import org.junit.Test

// Os casos de fatura.spec.ts e de FaturaTests.swift.
class FaturaTest {

    @Test
    fun `fecha 7 dias antes do vencimento, voltando ao mes anterior`() {
        assertEquals(8, fechamentoPadrao(15))
        assertEquals(29, fechamentoPadrao(5))
    }

    @Test
    fun `na vespera do fechamento cai na fatura do mes, no dia do fechamento na seguinte`() {
        assertEquals(FaturaDaCompra("2026-10", "2026-10-15"), faturaDaCompra(8, 15, "2026-10-07"))
        assertEquals(FaturaDaCompra("2026-11", "2026-11-15"), faturaDaCompra(8, 15, "2026-10-08"))
    }

    @Test
    fun `vencimento antes do fechamento fica no mes seguinte, dia 31 vira o fim do mes curto`() {
        assertEquals(FaturaDaCompra("2026-11", "2026-12-05"), faturaDaCompra(29, 5, "2026-10-30"))
        assertEquals(FaturaDaCompra("2026-03", "2026-04-10"), faturaDaCompra(31, 10, "2026-02-28"))
    }

    @Test
    fun `vira o ano`() {
        assertEquals(FaturaDaCompra("2027-01", "2027-02-05"), faturaDaCompra(25, 5, "2026-12-26"))
    }
}
