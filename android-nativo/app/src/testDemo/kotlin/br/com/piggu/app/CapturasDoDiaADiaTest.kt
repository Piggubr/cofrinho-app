package br.com.piggu.app

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import br.com.piggu.app.moldura.PerfilDaMoldura
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.designsystem.PigguTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.inject.Inject
import javax.inject.Named
import coil3.SingletonImageLoader
import okhttp3.OkHttpClient

/** A conta da demonstração: Ana, titular com Premium, todos os módulos ligados. */
private val ANA = PerfilDaMoldura(nome = "Ana Souza", email = "ana@demo.piggu.app", ehTitular = true, premium = true, modulos = null)

/**
 * As telas do dia a dia (A4) com o grafo do Hilt de verdade: a API falsa da demonstração
 * responde, no dia das capturas (28/10/2026), sem atraso (ModulosDasCapturas).
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [36])
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTestApi::class)
class CapturasDoDiaADiaTest(private val aparelho: String, private val tema: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun casos() = listOf("celular", "tablet").flatMap { a -> listOf("claro", "escuro").map { arrayOf(a, it) } }
    }

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<AtividadeDeTeste>()

    @Inject lateinit var sessao: Sessao
    @Inject lateinit var cliente: OkHttpClient
    @Inject @Named("urlDaApi") lateinit var urlDaApi: String

    @Before
    fun preparar() {
        RuntimeEnvironment.setQualifiers(if (aparelho == "celular") RobolectricDeviceQualifiers.Pixel7 else RobolectricDeviceQualifiers.MediumTablet)
        if (tema == "escuro") RuntimeEnvironment.setQualifiers("+night")
        comInternet()
        hilt.inject()
        // O HiltTestApplication não é o PigguApplication: o carregador das fotos vem daqui.
        SingletonImageLoader.setUnsafe { carregadorDeImagens(it, urlDaApi, cliente) }
        runBlocking { sessao.entrarComSenha("ana@demo.piggu.app", "senha123", lembrar = false) }
        compose.setContent {
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                PigguTheme { AppLogado(ANA, emptyFlow(), SnackbarHostState(), abrirNoSite = {}, sair = {}) }
            }
        }
    }

    /** O Robolectric começa sem rede validada: as telas diriam "sem internet" (MonitorDeRede). */
    private fun comInternet() {
        val conectividade = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSystemService(ConnectivityManager::class.java)
        val capacidades = ShadowNetworkCapabilities.newInstance().also {
            shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        shadowOf(conectividade).setNetworkCapabilities(conectividade.activeNetwork, capacidades)
    }

    /** Espera a API falsa (outra thread) responder e o esqueleto sair. */
    private fun esperarCarregar() {
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasContentDescription("Carregando…")).fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
    }

    private fun capturar(nome: String, etapa: Int = 4) =
        compose.onRoot().captureRoboImage("../docs/prints/etapa-$etapa/$nome-$aparelho-$tema.png")

    /** Uma tela do Mais: no celular pela aba Mais, no tablet direto na gaveta. */
    private fun abrirDoMais(rotulo: String) {
        esperarCarregar()
        if (aparelho == "celular") tocar("Mais")
        // A lista do Mais (ou a gaveta) só compõe o que aparece: rola até o item, se preciso.
        val listas = compose.onAllNodes(hasScrollToNodeAction())
        for (i in listas.fetchSemanticsNodes().indices) {
            if (compose.onAllNodes(hasText(rotulo) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) break
            runCatching { listas[i].performScrollToNode(hasText(rotulo) and hasClickAction()) }
        }
        tocar(rotulo)
        esperarCarregar()
    }

    /** A aba (barra, trilho ou gaveta) ou a opção com esse texto; o primeiro que der para tocar. */
    private fun tocar(texto: String) =
        compose.onAllNodes(hasText(texto) and hasClickAction()).onFirst().performClick()

    @Test
    fun painel() {
        esperarCarregar()
        capturar("painel")
    }

    @Test
    fun gastos() {
        tocar("Gastos")
        esperarCarregar()
        capturar("gastos")
    }

    @Test
    fun receitas() {
        tocar("Receitas")
        esperarCarregar()
        capturar("receitas")
    }

    @Test
    fun lancarGasto() {
        esperarCarregar()
        compose.onNode(hasContentDescription("Lançar gasto ou receita")).performClick()
        tocar("Lançar gasto")
        esperarCarregar()
        capturar("lancar-gasto")
    }

    @Test
    fun lancarReceita() {
        esperarCarregar()
        compose.onNode(hasContentDescription("Lançar gasto ou receita")).performClick()
        tocar("Lançar receita")
        esperarCarregar()
        capturar("lancar-receita")
    }

    @Test
    fun busca() {
        esperarCarregar()
        compose.onNode(hasContentDescription("Buscar")).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("pada")
        // O debounce da busca (no relógio do Compose) e a resposta da API falsa (outra thread).
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("Padaria", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        esperarCarregar()
        capturar("busca")
    }

    @Test
    fun assistente() {
        esperarCarregar()
        compose.onNode(hasContentDescription("Assistente")).performClick()
        esperarCarregar()
        capturar("assistente")
    }

    // Etapa A5: organização.

    @Test
    fun relatorios() {
        tocar("Relatórios")
        esperarCarregar()
        capturar("relatorios", 5)
    }

    @Test
    fun contasDoMes() {
        abrirDoMais("Contas do mês")
        capturar("contas-do-mes", 5)
    }

    @Test
    fun orcamento() {
        abrirDoMais("Orçamento")
        capturar("orcamento", 5)
    }

    @Test
    fun calendario() {
        abrirDoMais("Calendário")
        capturar("calendario", 5)
    }

    @Test
    fun cartoesEBancos() {
        abrirDoMais("Cartões e bancos")
        capturar("cartoes-e-bancos", 5)
    }

    // Etapa A6: metas e conta.

    @Test
    fun metas() {
        abrirDoMais("Minhas metas")
        capturar("metas", 6)
    }

    @Test
    fun girlsMath() {
        abrirDoMais("Girls Math")
        capturar("girls-math", 6)
    }

    @Test
    fun planejar() {
        abrirDoMais("Planejar")
        capturar("planejar", 6)
    }

    @Test
    fun premios() {
        abrirDoMais("Prêmios")
        capturar("premios", 6)
    }

    @Test
    fun familia() {
        abrirDoMais("Família")
        capturar("familia", 6)
    }

    @Test
    fun plano() {
        abrirDoMais("Premium")
        capturar("plano", 6)
    }

    @Test
    fun perfil() {
        abrirDoMais("Ana Souza")
        capturar("perfil", 6)
    }

    // Etapa A7: estilo de vida.

    @Test
    fun compras() {
        abrirDoMais("Compras")
        capturar("compras", 7)
    }

    @Test
    fun fotos() {
        abrirDoMais("Fotos")
        esperarImagens()
        capturar("fotos", 7)
    }

    /** O Coil carrega fora da thread principal: espera as fotos da API falsa chegarem. */
    private fun esperarImagens() {
        Thread.sleep(1_500)
        compose.waitForIdle()
    }
}
