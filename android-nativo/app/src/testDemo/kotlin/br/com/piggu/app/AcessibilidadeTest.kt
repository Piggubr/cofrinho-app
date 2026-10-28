package br.com.piggu.app

import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.designsystem.PigguTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.inject.Inject

/** A Ana da demonstração, como nas capturas da A4: titular, Premium, todos os módulos. */
internal val CONTA_DA_DEMO = br.com.piggu.app.moldura.PerfilDaMoldura("Ana Souza", "ana@demo.piggu.app", ehTitular = true, premium = true, modulos = null)

/**
 * O TalkBack nas telas principais, com a demonstração: todo nó que dá para tocar tem um rótulo
 * (texto ou contentDescription) e a área de toque de 48dp, e as telas marcam os títulos.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class AcessibilidadeTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<AtividadeDeTeste>()

    @Inject lateinit var sessao: Sessao

    @Before
    fun preparar() {
        comInternet()
        hilt.inject()
        runBlocking { sessao.entrarComSenha("ana@demo.piggu.app", "senha123", lembrar = false) }
        compose.setContent {
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                PigguTheme { AppLogado(CONTA_DA_DEMO, emptyFlow(), SnackbarHostState(), abrirNoSite = {}, sair = {}) }
            }
        }
        esperarCarregar()
    }

    private fun esperarCarregar() {
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasContentDescription("Carregando…")).fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
    }

    private fun tocar(texto: String) =
        compose.onAllNodes(hasText(texto) and hasClickAction()).onFirst().performClick()

    /**
     * Um nó tocável sem rótulo (texto ou contentDescription). A área de toque não entra: o Compose
     * já estende o toque de todo pointerInput até 48dp (minimumTouchTargetSize).
     */
    private fun problemas(no: SemanticsNode): List<String> {
        val c = no.config
        val rotulo = c.getOrNull(SemanticsProperties.Text).orEmpty().joinToString().ifBlank { null }
            ?: c.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString().ifBlank { null }
        return listOfNotNull(if (rotulo == null) "sem rótulo em ${no.boundsInRoot} (${c.getOrNull(SemanticsProperties.Role)})" else null)
    }

    /** Confere a tela e o que aparece rolando a lista principal até o fim. */
    private fun conferir(tela: String) {
        val achados = linkedSetOf<String>()
        val vertical = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        repeat(8) {
            compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().flatMapTo(achados, ::problemas)
            val listas = compose.onAllNodes(vertical).fetchSemanticsNodes()
            if (listas.isEmpty()) return@repeat
            compose.onAllNodes(vertical).onFirst().performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        assertTrue("$tela:\n" + achados.joinToString("\n"), achados.isEmpty())
        val titulos = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).fetchSemanticsNodes()
        assertTrue("$tela sem nenhum título marcado como heading()", titulos.isNotEmpty())
    }

    @Test
    fun painel() = conferir("Painel")

    @Test
    fun gastos() {
        tocar("Gastos")
        esperarCarregar()
        conferir("Gastos")
    }

    @Test
    fun lancarGasto() {
        compose.onNode(hasContentDescription("Lançar gasto ou receita")).performClick()
        tocar("Lançar gasto")
        esperarCarregar()
        conferir("Lançar gasto")
    }

    @Test
    fun perfil() {
        tocar("Mais")
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Ana Souza") and hasClickAction())
        tocar("Ana Souza")
        esperarCarregar()
        conferir("Perfil")
    }
}
