package br.com.piggu.core.ui.formatos

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

// datas.spec.ts e FormatosTests.swift: dia civil, sem fuso.
class DatasTest {

    @Test
    fun `monta a chave de mes com dois digitos`() {
        assertEquals("2026-01", chaveDoMes(LocalDate.of(2026, 1, 15)))
        assertEquals("2026-12", chaveDoMes(LocalDate.of(2026, 12, 1)))
    }

    @Test
    fun `mes e dia como a API, e somar meses virando o ano`() {
        val data = LocalDate.parse("2026-03-07")
        assertEquals("2026-03", chaveDoMes(data))
        assertEquals("2026-03-07", data.toString())
        assertEquals("2025-12", chaveDoMes(data.minusMonths(3)))
        assertEquals("2027-01", chaveDoMes(LocalDate.of(2026, 12, 1).plusMonths(1)))
    }

    @Test
    fun `mostra o mes por extenso`() {
        assertEquals("Setembro de 2026", mesPorExtenso("2026-09"))
        assertEquals("Janeiro de 2026", mesPorExtenso("2026-01"))
        assertEquals("Março de 2026", mesPorExtenso("2026-03"))
    }

    @Test
    fun `devolve a chave crua quando o mes nao faz sentido`() {
        assertEquals("2026-99", mesPorExtenso("2026-99"))
    }

    @Test
    fun `formata a data no padrao brasileiro sem deslocar o dia`() {
        assertEquals("05/09/2026", dataCurta("2026-09-05"))
        assertEquals("01/01/2026", dataCurta("2026-01-01T10:00:00Z"))
        assertEquals("2026-09", dataCurta("2026-09"))
    }

    @Test
    fun `trata data ausente sem quebrar`() {
        assertEquals("", dataCurta(null))
        assertEquals("", dataCurta(""))
    }

    @Test
    fun `monta a grade do mes com os vazios do comeco`() {
        // Setembro de 2026 começa numa terça-feira: duas células vazias antes do dia 1.
        val grade = gradeDoMes(YearMonth.of(2026, 9))
        assertEquals(listOf(null, null, 1), grade.take(3))
        assertEquals(30, grade.count { it != null })
        // Novembro de 2026 começa num domingo: nenhum vazio.
        assertEquals(1, gradeDoMes(YearMonth.of(2026, 11)).first())
    }
}
