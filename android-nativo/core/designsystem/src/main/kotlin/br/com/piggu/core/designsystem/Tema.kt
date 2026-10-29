package br.com.piggu.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/** As cores do perfil, iguais às do site (frontend/src/styles.scss). Chiclete é a padrão (D-071). */
enum class VarianteDeCor { Chiclete, Azul, Lilas, Menta }

// Sora é uma fonte variável (OFL, OFL-Sora.txt): um arquivo só, um peso por eixo wght.
@OptIn(ExperimentalTextApi::class)
private val Sora = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map {
        Font(R.font.sora, it, variationSettings = FontVariation.Settings(FontVariation.weight(it.weight)))
    },
)

private fun TextStyle.emSora() = copy(fontFamily = Sora)

private val Tipografia = Typography().run {
    Typography(
        displayLarge = displayLarge.emSora(),
        displayMedium = displayMedium.emSora(),
        displaySmall = displaySmall.emSora(),
        headlineLarge = headlineLarge.emSora(),
        headlineMedium = headlineMedium.emSora(),
        headlineSmall = headlineSmall.emSora(),
        titleLarge = titleLarge.emSora(),
        titleMedium = titleMedium.emSora(),
        titleSmall = titleSmall.emSora(),
        bodyLarge = bodyLarge.emSora(),
        bodyMedium = bodyMedium.emSora(),
        bodySmall = bodySmall.emSora(),
        labelLarge = labelLarge.emSora(),
        labelMedium = labelMedium.emSora(),
        labelSmall = labelSmall.emSora(),
    )
}

@Composable
fun PigguTheme(
    variante: VarianteDeCor = VarianteDeCor.Chiclete,
    escuro: Boolean = isSystemInDarkTheme(),
    // Cores do papel de parede (Material You), só no Android 12 ou mais novo.
    coresDinamicas: Boolean = false,
    content: @Composable () -> Unit,
) {
    val cores = when {
        coresDinamicas && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val contexto = LocalContext.current
            if (escuro) dynamicDarkColorScheme(contexto) else dynamicLightColorScheme(contexto)
        }
        else -> when (variante) {
            VarianteDeCor.Chiclete -> if (escuro) ChicleteEscuro else ChicleteClaro
            VarianteDeCor.Azul -> if (escuro) AzulEscuro else AzulClaro
            VarianteDeCor.Lilas -> if (escuro) LilasEscuro else LilasClaro
            VarianteDeCor.Menta -> if (escuro) MentaEscuro else MentaClaro
        }
    }
    MaterialTheme(colorScheme = cores, typography = Tipografia, content = content)
}
