package br.com.piggu.feature.relatorios

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.PigguTheme
import br.com.piggu.core.model.CategoriaNoAno
import br.com.piggu.core.model.CategoriaNoMes
import br.com.piggu.core.model.MesDoAno
import br.com.piggu.core.model.RelatorioDoAno
import br.com.piggu.core.model.ResumoDoMes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.math.BigDecimal

private val RESUMO = ResumoDoMes(
    mes = "2026-09",
    receitas = BigDecimal(1000),
    gastos = BigDecimal(300),
    sobra = BigDecimal(700),
    gastosMesAnterior = BigDecimal(200),
    variacao = BigDecimal(50),
    projecaoDeGastos = BigDecimal(900),
    porCategoria = listOf(CategoriaNoMes("Mercado", BigDecimal(300), BigDecimal(200))),
)

private val ANO = RelatorioDoAno(
    ano = 2026,
    receitas = BigDecimal(1000),
    gastos = BigDecimal(300),
    sobra = BigDecimal(700),
    meses = (1..12).map { i ->
        val v = if (i == 9) 1 else 0
        MesDoAno("2026-%02d".format(i), BigDecimal(1000 * v), BigDecimal(300 * v), BigDecimal(700 * v))
    },
    porCategoria = listOf(CategoriaNoAno("Mercado", BigDecimal(300))),
)

/** A tela sem estado (a das capturas), com os dados do reports.spec.ts do site. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TelaDeRelatoriosTest {
    @get:Rule val compose = createComposeRule()

    private fun estado(premium: Boolean) = EstadoDeRelatorios(
        mes = "2026-09",
        mesAtual = "2026-10",
        moeda = "BRL",
        resumo = Carga.Pronto(RESUMO),
        ehPremium = premium,
        doAno = if (premium) Carga.Pronto(ANO) else null,
        tabelaDoAno = true,
        mesEmFoco = 8,
    )

    /** A lista é preguiçosa: o que fica abaixo da dobra só existe depois de rolar. */
    private fun rolarAte(texto: String) =
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(texto)).let { compose.onNodeWithText(texto) }

    @Test
    fun `mostra a comparacao, a projecao e o ano com a tabela`() {
        compose.setContent { PigguTheme { TelaDeRelatorios(estado(premium = true), EventosDeRelatorios()) } }
        compose.onNodeWithText("▲ 50% a mais").assertExists()
        compose.onNodeWithText("Projeção do mês").assertExists()
        rolarAte("Set 2026").assertExists()
        rolarAte("Para onde foi o dinheiro").assertExists()
    }

    @Test
    fun `sem Premium mostra o convite e chama o plano`() {
        var abriu = 0
        compose.setContent {
            PigguTheme { TelaDeRelatorios(estado(premium = false).copy(conectado = false), EventosDeRelatorios(abrirPremium = { abriu++ })) }
        }
        compose.onNodeWithText("Exportar CSV do mês").assertIsNotEnabled()
        rolarAte("Conhecer o Piggu Premium").performClick()
        assertEquals(1, abriu)
        compose.onAllNodesWithText("Para onde foi o dinheiro").assertCountEquals(0)
    }
}
