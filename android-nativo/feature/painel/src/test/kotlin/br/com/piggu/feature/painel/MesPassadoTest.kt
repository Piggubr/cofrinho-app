package br.com.piggu.feature.painel

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.PigguTheme
import br.com.piggu.core.model.Cofrinho
import br.com.piggu.core.model.Gasto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.math.BigDecimal

/** Começo do mês (D-165): o mês de hoje ainda sem gasto mostra o anterior como referência. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class MesPassadoTest {
    @get:Rule val compose = createComposeRule()

    private fun gasto(id: String, data: String, categoria: String = "Casa", valor: String = "10", registradoEm: String? = null) =
        Gasto(id = id, data = data, estabelecimento = "Loja $id", item = id, categoria = categoria, valor = BigDecimal(valor), registradoEm = registradoEm)

    private fun estado(doMes: List<Gasto>, passados: List<Gasto>) = EstadoDoPainel(
        mes = "2026-11",
        mesAtual = "2026-11",
        hoje = "2026-11-02",
        moeda = "BRL",
        carga = Carga.Pronto(Nucleo(Cofrinho(totalDepositos = BigDecimal.ZERO, totalGastos = BigDecimal.ZERO, saldo = BigDecimal.ZERO), doMes, emptyMap())),
        gastosDoMesPassado = passados,
    )

    @Test
    fun `os cinco mais recentes, por data e hora, e as categorias somadas`() {
        val e = estado(
            emptyList(),
            listOf(
                gasto("a", "2026-10-01", "Mercado", "80"), gasto("b", "2026-10-30"),
                gasto("c", "2026-10-15"), gasto("d", "2026-10-30", registradoEm = "2026-10-30T20:00:00"),
                gasto("e", "2026-10-20"), gasto("f", "2026-10-10"),
            ),
        )
        assertTrue(e.mostraMesPassado)
        assertEquals(listOf("d", "b", "e", "c", "f"), e.recentesDoMesPassado.map { it.id })
        assertEquals(listOf("Mercado", "Casa"), e.porCategoriaDoMesPassado.map { it.categoria })
    }

    @Test
    fun `some no primeiro gasto do mes`() {
        assertFalse(estado(listOf(gasto("n", "2026-11-01")), listOf(gasto("a", "2026-10-01"))).mostraMesPassado)
        assertFalse(estado(emptyList(), emptyList()).mostraMesPassado)
    }

    @Test
    fun `o cartao diz de que mes sao os gastos`() {
        compose.setContent { PigguTheme { CartaoDoMesPassado(estado(emptyList(), listOf(gasto("a", "2026-10-01"))), {}, {}) } }
        compose.onNodeWithText("Como foi outubro de 2026").assertExists()
        compose.onNodeWithText("Loja a").assertExists()
        compose.onNodeWithText("Ver o ano").assertExists()
    }
}
