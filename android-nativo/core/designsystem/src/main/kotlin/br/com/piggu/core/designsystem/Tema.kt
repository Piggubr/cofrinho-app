package br.com.piggu.core.designsystem

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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

/**
 * Cor do Piggu (Perfil > Cor do Piggu): o TEMAS de frontend/src/app/core/ui/tema.ts, na mesma
 * ordem. O [id] é o valor que o site grava no aparelho (chave `piggu_tema`). Chiclete é a padrão
 * (D-071).
 */
enum class VarianteDeCor(
    val id: String,
    @StringRes val nome: Int,
    @StringRes val descricao: Int,
    val premium: Boolean,
) {
    Chiclete("chiclete", R.string.cor_chiclete, R.string.cor_chiclete_descricao, premium = false),
    Rosa("rosa", R.string.cor_rosa, R.string.cor_rosa_descricao, premium = false),
    Menta("menta", R.string.cor_menta, R.string.cor_menta_descricao, premium = false),
    Azul("azul", R.string.cor_azul, R.string.cor_azul_descricao, premium = false),
    Lilas("lilas", R.string.cor_lilas, R.string.cor_lilas_descricao, premium = true),
    Coral("coral", R.string.cor_coral, R.string.cor_coral_descricao, premium = true),
    Mel("mel", R.string.cor_mel, R.string.cor_mel_descricao, premium = true),
    Vinho("vinho", R.string.cor_vinho, R.string.cor_vinho_descricao, premium = true),
    Grafite("grafite", R.string.cor_grafite, R.string.cor_grafite_descricao, premium = true),
    Tangerina("tangerina", R.string.cor_tangerina, R.string.cor_tangerina_descricao, premium = true),
    Eletrico("eletrico", R.string.cor_eletrico, R.string.cor_eletrico_descricao, premium = true),
    Turquesa("turquesa", R.string.cor_turquesa, R.string.cor_turquesa_descricao, premium = true),
    Cobalto("cobalto", R.string.cor_cobalto, R.string.cor_cobalto_descricao, premium = true),
    ;

    /**
     * A cor que vale, como o effect do `Temas` no site: com a pessoa logada sem Premium, uma cor
     * do Premium volta para o Chiclete. [premium] nulo é antes do login, quando o plano ainda não
     * se sabe, e nada muda. Voltou (o resultado é outro): grave o Chiclete e marque o aviso.
     */
    fun efetiva(premium: Boolean?): VarianteDeCor = if (this.premium && premium == false) Chiclete else this

    companion object {
        /** Id nulo ou desconhecido (uma cor que saiu do site, valor estragado) vira o Chiclete. */
        fun deId(id: String?): VarianteDeCor = entries.firstOrNull { it.id == id } ?: Chiclete
    }
}

/** Perfil > Aparência, como o site: Automático segue o aparelho. O [id] é o da chave `piggu_modo`. */
enum class ModoDeCor(val id: String, @StringRes val nome: Int) {
    Automatico("automatico", R.string.modo_automatico),
    Claro("claro", R.string.modo_claro),
    Escuro("escuro", R.string.modo_escuro),
    ;

    companion object {
        fun deId(id: String?): ModoDeCor = entries.firstOrNull { it.id == id } ?: Automatico
    }
}

private fun VarianteDeCor.esquema(escuro: Boolean): ColorScheme = when (this) {
    VarianteDeCor.Chiclete -> if (escuro) ChicleteEscuro else ChicleteClaro
    VarianteDeCor.Rosa -> if (escuro) RosaEscuro else RosaClaro
    VarianteDeCor.Menta -> if (escuro) MentaEscuro else MentaClaro
    VarianteDeCor.Azul -> if (escuro) AzulEscuro else AzulClaro
    VarianteDeCor.Lilas -> if (escuro) LilasEscuro else LilasClaro
    VarianteDeCor.Coral -> if (escuro) CoralEscuro else CoralClaro
    VarianteDeCor.Mel -> if (escuro) MelEscuro else MelClaro
    VarianteDeCor.Vinho -> if (escuro) VinhoEscuro else VinhoClaro
    VarianteDeCor.Grafite -> if (escuro) GrafiteEscuro else GrafiteClaro
    VarianteDeCor.Tangerina -> if (escuro) TangerinaEscuro else TangerinaClaro
    VarianteDeCor.Eletrico -> if (escuro) EletricoEscuro else EletricoClaro
    VarianteDeCor.Turquesa -> if (escuro) TurquesaEscuro else TurquesaClaro
    VarianteDeCor.Cobalto -> if (escuro) CobaltoEscuro else CobaltoClaro
}

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
    modo: ModoDeCor = ModoDeCor.Automatico,
    // Cores do celular (do papel de parede, Material You), só no Android 12 ou mais novo.
    coresDinamicas: Boolean = false,
    content: @Composable () -> Unit,
) {
    val escuro = when (modo) {
        ModoDeCor.Automatico -> isSystemInDarkTheme()
        ModoDeCor.Claro -> false
        ModoDeCor.Escuro -> true
    }
    val cores = when {
        coresDinamicas && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val contexto = LocalContext.current
            if (escuro) dynamicDarkColorScheme(contexto) else dynamicLightColorScheme(contexto)
        }
        else -> variante.esquema(escuro)
    }
    MaterialTheme(colorScheme = cores, typography = Tipografia, content = content)
}
