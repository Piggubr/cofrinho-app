package br.com.piggu.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contraste do WCAG 2: (L1 + 0,05) / (L2 + 0,05), com a luminância relativa de cada cor. */
private fun contraste(a: Color, b: Color): Double {
    val (claro, escuro) = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sortedDescending()
    return (claro + 0.05) / (escuro + 0.05)
}

/** Texto sobre fundo, nas combinações que as telas usam. */
private val pares: List<Triple<String, (ColorScheme) -> Color, (ColorScheme) -> Color>> = listOf(
    Triple("onPrimary sobre primary", { it.onPrimary }, { it.primary }),
    Triple("onSurface sobre surface", { it.onSurface }, { it.surface }),
    Triple("onSurfaceVariant sobre surfaceVariant", { it.onSurfaceVariant }, { it.surfaceVariant }),
    Triple("onSurfaceVariant sobre surface", { it.onSurfaceVariant }, { it.surface }),
    Triple("onError sobre error", { it.onError }, { it.error }),
    Triple("onPrimaryContainer sobre primaryContainer", { it.onPrimaryContainer }, { it.primaryContainer }),
    Triple("onSecondaryContainer sobre secondaryContainer", { it.onSecondaryContainer }, { it.secondaryContainer }),
    Triple("onErrorContainer sobre errorContainer", { it.onErrorContainer }, { it.errorContainer }),
    // O texto na cor do tema (o rosa do Chiclete) sobre o fundo e sobre os cartões.
    Triple("primary sobre background", { it.primary }, { it.background }),
    Triple("primary sobre surface", { it.primary }, { it.surface }),
    Triple("primary sobre surfaceContainerLow", { it.primary }, { it.surfaceContainerLow }),
    Triple("primary sobre surfaceContainer", { it.primary }, { it.surfaceContainer }),
    // O Card do Material 3 pinta o fundo com surfaceContainerHighest.
    Triple("primary sobre surfaceContainerHighest", { it.primary }, { it.surfaceContainerHighest }),
    Triple("onSurfaceVariant sobre surfaceContainerHighest", { it.onSurfaceVariant }, { it.surfaceContainerHighest }),
    Triple("error sobre surface", { it.error }, { it.surface }),
)

class ContrasteTest {

    @Test
    fun `texto das 13 cores em claro e escuro passa no WCAG AA (4,5 para 1)`() {
        val falhas = VarianteDeCor.entries.flatMap { variante ->
            listOf(false, true).flatMap { escuro ->
                val cores = variante.esquema(escuro)
                pares.mapNotNull { (nome, texto, fundo) ->
                    val c = contraste(texto(cores), fundo(cores))
                    if (c < 4.5) "${variante.name} ${if (escuro) "escuro" else "claro"}: $nome = ${"%.2f".format(c)}" else null
                }
            }
        }
        assertTrue("Contraste abaixo de 4,5:1:\n" + falhas.joinToString("\n"), falhas.isEmpty())
    }

    @Test
    fun `a conta do contraste confere com os extremos`() {
        assertTrue(contraste(Color.Black, Color.White) in 20.99..21.01)
        assertTrue(contraste(Color.White, Color.White) in 0.99..1.01)
    }
}
