package br.com.piggu.app

import android.app.Application
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import br.com.piggu.core.designsystem.PigguTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

    @Test
    fun abas() {
        val tela = if (aparelho == "celular") RobolectricDeviceQualifiers.Pixel7 else RobolectricDeviceQualifiers.MediumTablet
        RuntimeEnvironment.setQualifiers(tela)
        if (tema == "escuro") RuntimeEnvironment.setQualifiers("+night")
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            // Sem o efeito de toque, que deixaria cada foto num ponto diferente da animação.
            CompositionLocalProvider(LocalRippleConfiguration provides null) { PigguTheme { PigguApp() } }
        }
        Aba.entries.forEach { aba ->
            compose.onNode(hasText(contexto.getString(aba.rotulo)) and hasClickAction()).performClick()
            compose.onRoot().captureRoboImage("../docs/prints/etapa-0/${aba.name.lowercase()}-$aparelho-$tema.png")
        }
    }
}
