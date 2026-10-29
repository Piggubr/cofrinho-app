package br.com.piggu.feature.calendario

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.PigguTheme
import br.com.piggu.core.model.Gasto
import br.com.piggu.core.model.Nota
import br.com.piggu.core.model.Receita
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.math.BigDecimal

/** O texto com o espaço que não quebra (o do R$ e do "mil") trocado pelo comum. */
private fun String.simples() = replace('\u00A0', ' ').replace('\u202F', ' ')

/** O nó cuja descrição para o TalkBack contém [texto]. */
private fun lidoComo(texto: String) = SemanticsMatcher("lido como \"$texto\"") { no ->
    no.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.simples().contains(texto) }
}

/** A tela sem estado como o TalkBack lê: o dia inteiro, a tabela por semana e o aviso do gasto junto. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TelaDoCalendarioTest {
    @get:Rule val compose = createComposeRule()

    private val revisao = Nota("n1", "Revisão do carro", "Na oficina", "2026-10-28", BigDecimal(450), "Transporte", gastoId = "g9", criadoEm = "x")
    private val estado = EstadoDoCalendario(
        mes = "2026-10",
        hoje = "2026-10-28",
        moeda = "BRL",
        diaEscolhido = "2026-10-28",
        dados = Carga.Pronto(
            DadosDoCalendario(
                notas = listOf(revisao),
                gastos = listOf(Gasto("g1", "2026-10-28", item = "Ração", categoria = "Pets", valor = BigDecimal("129.90"))),
                receitas = listOf(Receita("r1", "2026-10-05", "Salário", "Salário", BigDecimal(6500))),
            ),
        ),
    )

    @Test
    fun `o TalkBack le o dia inteiro e a tabela por semana`() {
        compose.setContent { PigguTheme { TelaDoCalendario(estado, EventosDoCalendario(), ladoALado = false) } }
        compose.onNode(lidoComo("quarta-feira, 28 de outubro, hoje, gasto de R\$ 129,90, 1 lembrete")).assertIsSelected()
        compose.onNode(lidoComo("segunda-feira, 5 de outubro, receita de R\$ 6.500,00")).assertExists()
        val lista = compose.onNode(hasScrollAction())
        lista.performScrollToNode(hasText("Ver por semana"))
        compose.onNodeWithText("Ver por semana").performClick()
        lista.performScrollToNode(lidoComo("Dias 1 a 7: receitas R\$ 6.500,00, gastos R\$ 0,00, sobra R\$ 6.500,00"))
    }

    @Test
    fun `apagar nota com gasto avisa que o gasto vai junto`() {
        compose.setContent { PigguTheme { TelaDoCalendario(estado.copy(apagando = revisao), EventosDoCalendario(), ladoALado = true) } }
        compose.onNodeWithText("Apagar \"Revisão do carro\"?").assertExists()
        compose.onNodeWithText("O gasto lançado junto também será apagado.").assertExists()
    }

    @Test
    fun `valor curto da grade e do eixo`() {
        assertEquals("6,5 mil", compacto(BigDecimal(6500)).simples())
        assertEquals("412,3", compacto(BigDecimal("412.30")).simples())
        assertEquals("10,7 mil", compacto(BigDecimal(10700)).simples())
    }
}
