package br.com.piggu.app

import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.designsystem.PigguTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.inject.Inject
import okhttp3.OkHttpClient

/**
 * A A9: a fonte do sistema em 200% no celular, e o Painel e Gastos do menor ao maior aparelho,
 * conferindo a moldura de cada largura (barra, trilho ou gaveta).
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [36])
class CapturasDaEtapa9Test(private val caso: Caso) {

    /** [moldura] nula: não confere (a fonte grande não muda a largura). */
    class Caso(val nome: String, val qualificadores: String, val fonte: Float, val moldura: String?, val telas: Set<String>) {
        override fun toString() = nome
    }

    companion object {
        private val TODAS = setOf("painel", "gastos", "lancar-gasto", "perfil")
        private val PAINEL_E_GASTOS = setOf("painel", "gastos")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun casos() = listOf(
            Caso("fonte-grande", RobolectricDeviceQualifiers.Pixel7, 2f, null, TODAS),
            Caso("tamanho-celular-pequeno", "w320dp-h568dp-normal-long-notround-any-xhdpi-keyshidden-nonav", 1f, "barra", PAINEL_E_GASTOS),
            Caso("tamanho-dobravel", "w673dp-h841dp-normal-notlong-notround-any-420dpi-keyshidden-nonav", 1f, "trilho", PAINEL_E_GASTOS),
            Caso("tamanho-tablet", RobolectricDeviceQualifiers.MediumTablet, 1f, "gaveta", PAINEL_E_GASTOS),
            Caso("tamanho-tela-grande", RobolectricDeviceQualifiers.LargeDesktop, 1f, "gaveta", PAINEL_E_GASTOS),
        ).map { arrayOf(it) }
    }

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<AtividadeDeTeste>()

    @Inject lateinit var sessao: Sessao
    @Inject lateinit var cliente: OkHttpClient

    @Before
    fun preparar() {
        RuntimeEnvironment.setQualifiers(caso.qualificadores)
        RuntimeEnvironment.setFontScale(caso.fonte)
        comInternet()
        hilt.inject()
        runBlocking { sessao.entrarComSenha("ana@demo.piggu.app", "senha123", lembrar = false) }
        compose.setContent {
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                PigguTheme { AppLogado(CONTA_DA_DEMO, emptyFlow(), SnackbarHostState(), abrirNoSite = {}, sair = {}) }
            }
        }
    }

    /** Como em CapturasDoDiaADiaTest: nenhum pedido no ar, duas vezes seguidas, e o esqueleto fora. */
    private fun esperarCarregar() {
        var quieto = 0
        while (quieto < 2) {
            compose.waitUntil(15_000) {
                cliente.dispatcher.runningCallsCount() == 0 &&
                    compose.onAllNodes(hasContentDescription("Carregando…")).fetchSemanticsNodes().isEmpty()
            }
            compose.waitForIdle()
            quieto = if (cliente.dispatcher.runningCallsCount() == 0) quieto + 1 else 0
        }
    }

    private fun tocar(texto: String) =
        compose.onAllNodes(hasText(texto) and hasClickAction()).onFirst().performClick()

    /** A forma da moldura pela aba Gastos: embaixo é a barra; ao lado, estreita é o trilho e larga é a gaveta. */
    private fun conferirMoldura() {
        val esperada = caso.moldura ?: return
        val aba = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and hasText("Gastos")
        val no = compose.onAllNodes(aba).onFirst().fetchSemanticsNode()
        val dp = no.layoutInfo.density.density
        val forma = when {
            no.boundsInRoot.top > compose.onRoot().fetchSemanticsNode().boundsInRoot.height / 2 -> "barra"
            no.boundsInRoot.width / dp > 200 -> "gaveta"
            else -> "trilho"
        }
        assertEquals(esperada, forma)
    }

    private fun capturar(tela: String) =
        compose.onRoot().captureRoboImage("../docs/prints/etapa-9/${caso.nome}-$tela.png")

    private fun so(tela: String) = assumeTrue(tela in caso.telas)

    @Test
    fun painel() {
        so("painel")
        esperarCarregar()
        conferirMoldura()
        capturar("painel")
    }

    @Test
    fun gastos() {
        so("gastos")
        esperarCarregar()
        tocar("Gastos")
        // Espera também os nomes da família, que chegam numa carga à parte.
        esperarCarregar()
        conferirMoldura()
        capturar("gastos")
    }

    @Test
    fun lancarGasto() {
        so("lancar-gasto")
        esperarCarregar()
        compose.onNode(hasContentDescription("Lançar gasto ou receita")).performClick()
        tocar("Lançar gasto")
        esperarCarregar()
        capturar("lancar-gasto")
    }

    @Test
    fun perfil() {
        so("perfil")
        esperarCarregar()
        tocar("Mais")
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Ana Souza") and hasClickAction())
        tocar("Ana Souza")
        esperarCarregar()
        capturar("perfil")
    }
}
