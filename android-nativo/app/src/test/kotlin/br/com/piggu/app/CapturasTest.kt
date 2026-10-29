package br.com.piggu.app

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import br.com.piggu.app.moldura.PerfilDaMoldura
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.designsystem.PigguTheme
import br.com.piggu.core.ui.CampoDeCategoria
import br.com.piggu.core.ui.CampoDeValor
import br.com.piggu.core.ui.CasoDeErro
import br.com.piggu.core.ui.ChipsDeVisao
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.EstadoDeErro
import br.com.piggu.core.ui.FalhaAoCarregar
import br.com.piggu.core.ui.SeletorDeMes
import br.com.piggu.core.ui.TelaDeErro
import br.com.piggu.core.ui.Vazio
import br.com.piggu.core.ui.Visao
import br.com.piggu.feature.login.EstadoDoLogin
import br.com.piggu.feature.login.EventosDoLogin
import br.com.piggu.feature.login.TelaDeLogin
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal

/** A conta da demonstração (DadosDaDemo): Ana, titular com Premium, todos os módulos ligados. */
private val ANA = PerfilDaMoldura(
    nome = "Ana Souza",
    email = "ana@demo.piggu.app",
    ehTitular = true,
    premium = true,
    modulos = null,
)

// Grava com ./gradlew recordRoborazziDemoDebug e confere com verifyRoborazziDemoDebug.
// As imagens ficam em docs/prints/etapa-N/ e servem de referência para o verify.
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36])
@OptIn(ExperimentalMaterial3Api::class)
class CapturasTest(private val aparelho: String, private val tema: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun casos() = listOf("celular", "tablet").flatMap { a -> listOf("claro", "escuro").map { arrayOf(a, it) } }
    }

    @get:Rule
    val compose = createComposeRule()

    private val contexto get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun aparelho() {
        RuntimeEnvironment.setQualifiers(if (aparelho == "celular") RobolectricDeviceQualifiers.Pixel7 else RobolectricDeviceQualifiers.MediumTablet)
        if (tema == "escuro") RuntimeEnvironment.setQualifiers("+night")
    }

    private fun desenhar(conteudo: @Composable () -> Unit) = compose.setContent {
        // Sem o efeito de toque, que deixaria cada foto num ponto diferente da animação.
        CompositionLocalProvider(LocalRippleConfiguration provides null) { PigguTheme { conteudo() } }
    }

    private fun capturar(nome: String) =
        compose.onRoot().captureRoboImage("../docs/prints/etapa-2/$nome-$aparelho-$tema.png")

    @Test
    fun moldura() {
        desenhar { AppLogado(ANA, emptyFlow(), SnackbarHostState(), abrirNoSite = {}, sair = {}, acoesDaConta = false) }
        capturar("painel")
        // No tablet o Mais vira a gaveta com os grupos: o celular mostra a aba.
        if (aparelho == "celular") {
            compose.onNode(hasText(contexto.getString(R.string.tela_mais)) and hasClickAction()).performClick()
            capturar("mais")
        }
        compose.onNode(hasContentDescription(contexto.getString(R.string.lancar))).performClick()
        compose.waitForIdle()
        capturar("lancar")
    }

    @Test
    fun login() {
        desenhar { TelaDeLogin(EstadoDoLogin(email = "ana@demo.piggu.app"), EventosDoLogin(), mostrarGoogle = false) }
        capturar("login")
    }

    @Test
    fun erros() {
        desenhar { TelaDeErro(CasoDeErro.NaoEncontrada, {}, {}) }
        capturar("404")
    }

    @Test
    fun erroDoServidor() {
        desenhar { TelaDeErro(CasoDeErro.Servidor, {}, {}) }
        capturar("500")
    }

    /** Os componentes comuns de core:ui juntos, como uma tela de referência. */
    @Test
    fun componentes() {
        desenhar {
            Surface {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SeletorDeMes("2026-10", "2026-10", {})
                    ChipsDeVisao(Visao.Tudo, {}, moraComAlguem = true)
                    CampoDeValor(BigDecimal("1234.50"), {}, "BRL")
                    CampoDeCategoria(listOf("Mercado", "Restaurante"), "Mercado", {}, { Result.success(emptyList()) }, {})
                    Vazio(Icone.Recibo, "Nenhum gasto neste mês", texto = "Lance o primeiro pelo +.", acao = "Lançar um gasto" to {})
                    EstadoDeErro(FalhaAoCarregar.SemInternet, {})
                    Esqueleto(linhas = 3)
                }
            }
        }
        capturar("componentes")
    }
}
