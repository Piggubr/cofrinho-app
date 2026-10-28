package br.com.piggu.feature.calendario

import br.com.piggu.core.model.Gasto
import br.com.piggu.core.model.Nota
import br.com.piggu.core.model.Receita
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

private fun gasto(id: String, data: String, valor: Int) = Gasto(id, data, item = "Item", categoria = "Casa", valor = BigDecimal(valor))
private fun receita(id: String, data: String, valor: Int) = Receita(id, data, "Salário", "Salário", BigDecimal(valor))

/** Os mesmos casos de grafico-do-mes.spec.ts e do CalendarioTests do iOS. */
class ContasDoCalendarioTest {
    private val gastos = listOf(gasto("g1", "2026-09-02", 100), gasto("g2", "2026-09-02", 50), gasto("g3", "2026-09-20", 400), gasto("g4", "2026-08-31", 999))
    private val receitas = listOf(receita("r1", "2026-09-05", 3000), receita("r2", "2026-09-15", 200))

    @Test
    fun `mes passado vai ate o ultimo dia, acumulando e ignorando o que e de outro mes`() {
        val serie = serieDoMes("2026-09", gastos, receitas, "2026-10-20")
        assertEquals(30, serie.size)
        assertEquals(DiaDaSerie(2, BigDecimal.ZERO, BigDecimal(150), BigDecimal.ZERO, BigDecimal(150)), serie[1])
        assertEquals(BigDecimal(3000), serie[4].receitas)
        assertEquals(BigDecimal(3000), serie[4].receitasAteAqui)
        assertEquals(BigDecimal(3200), serie.last().receitasAteAqui)
        assertEquals(BigDecimal(550), serie.last().gastosAteAqui)
        assertEquals(BigDecimal(2650), serie.last().sobra)
    }

    @Test
    fun `mes corrente para em hoje e mes futuro fica vazio`() {
        assertEquals(10, serieDoMes("2026-09", gastos, receitas, "2026-09-10").size)
        assertTrue(serieDoMes("2026-11", gastos, receitas, "2026-10-20").isEmpty())
    }

    @Test
    fun `semanas do mes, ate as que ainda nao aconteceram`() {
        val semanas = semanasDoMes("2026-09", serieDoMes("2026-09", gastos, receitas, "2026-10-20"))
        assertEquals(listOf(1, 8, 15, 22, 29), semanas.map { it.de })
        assertEquals(30, semanas.last().ate)
        assertEquals(SemanaDoMes(15, 21, BigDecimal(200), BigDecimal(400)), semanas[2])
        assertEquals(BigDecimal(-200), semanas[2].sobra)
        assertEquals(BigDecimal(150), semanas[0].gastos)

        // No mês corrente, as semanas depois de hoje ficam zeradas.
        val outubro = semanasDoMes("2026-10", serieDoMes("2026-10", gastos, receitas, "2026-10-10"))
        assertEquals(listOf(7, 14, 21, 28, 31), outubro.map { it.ate })
        assertEquals(BigDecimal.ZERO, outubro.last().receitas)
    }

    @Test
    fun `a escala termina num numero redondo acima do maior valor`() {
        assertEquals(1.0, topoDaEscala(0.0), 0.0)
        assertEquals(4000.0, topoDaEscala(3200.0), 1e-9)
        assertEquals(12000.0, topoDaEscala(10700.0), 1e-9)
        assertEquals(2000.0, topoDaEscala(1800.0), 1e-9)
        assertEquals(2500.0, topoDaEscala(2100.0), 1e-9)
        assertEquals(100.0, topoDaEscala(100.0), 1e-9)
    }

    @Test
    fun `a grade comeca no domingo e soma gastos, receitas e lembretes do dia`() {
        val notas = listOf(
            Nota("n1", "Dentista", data = "2026-10-01", criadoEm = "x"),
            Nota("n2", "Mercado", data = "2026-10-01", criadoEm = "x"),
            Nota("n3", "Sem data", criadoEm = "x"),
        )
        val grade = gradeDoCalendario("2026-10", listOf(gasto("g", "2026-10-01", 30), gasto("h", "2026-10-01", 20)), listOf(receita("r", "2026-10-31", 9)), notas, "2026-10-28")
        // Outubro de 2026 começa numa quinta: quatro espaços antes do dia 1.
        assertEquals(listOf(null, null, null, null), grade.take(4))
        assertEquals(DiaDaGrade(1, "2026-10-01", BigDecimal(50), BigDecimal.ZERO, 2, false), grade[4])
        assertEquals(BigDecimal(9), grade.last()!!.receitas)
        assertTrue(grade.single { it?.dia == 28 }!!.hoje)
        assertEquals(35, grade.size)
    }

    @Test
    fun `o dedo vale o dia mais perto entre os que ja aconteceram`() {
        assertEquals(1, diaNoPonto(-0.2f, 31, 28))
        assertEquals(16, diaNoPonto(0.5f, 31, 31))
        assertEquals(28, diaNoPonto(1f, 31, 28))
        assertEquals(1, diaNoPonto(0.5f, 31, 0))
    }
}
