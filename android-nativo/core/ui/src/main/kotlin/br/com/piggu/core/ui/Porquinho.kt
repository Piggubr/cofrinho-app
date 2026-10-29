package br.com.piggu.core.ui

import android.provider.Settings
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** feliz na abertura; perdido na 404 (interrogação e a moeda fugindo); tonto na 500 e sem internet. */
enum class HumorDoPorquinho { Feliz, Perdido, Tonto }

/** As cores do porquinho do site (core/ui/porquinho.ts), tiradas do tema para mudar com a cor do Piggu. */
private class CoresDoPorquinho(esquema: ColorScheme, escuro: Boolean) {
    val corpo = esquema.primaryContainer
    val contorno = esquema.primary
    val rosa = lerp(esquema.primary, esquema.primaryContainer, 0.45f)
    val sombra = esquema.surfaceVariant
    val olho = esquema.onSurface
    val brilho = esquema.surface
    val ouro = if (escuro) Color(0xFFE0B45A) else Color(0xFFD4A64A)
    val ouroEscuro = if (escuro) Color(0xFFF0CD85) else Color(0xFF82601A)
}

private fun caminho(d: String): Path = PathParser().parsePathString(d).toPath()

private val RABO = caminho("M40 98c-14-1-17-15-7-18 8-2 9 9 0 10")
private val ORELHA_DE_TRAS = caminho("M104 58c-4-20 6-32 20-28 2 12-2 22-8 30z")
private val ORELHA = caminho("M124 60c-2-22 10-34 24-28 2 14-4 24-12 32z")
private val OLHO_X = caminho("M131 80l10 10M141 80l-10 10")
private val BOCA_TONTA = caminho("M144 121c3-3 5 3 8 0s5 3 8 0")
private val SOBRANCELHA = caminho("M128 72q8-7 17-3")
private val SORRISO = caminho("M146 118q6 6 12 0")
private val ESTRELAS = listOf(
    caminho("M70 22l3 7 7 1-5 5 1 7-6-3-6 3 1-7-5-5 7-1z"),
    caminho("M150 14l2.4 5.4 5.6.8-4 4 .9 5.6-4.9-2.6-5 2.6 1-5.6-4-4 5.6-.8z"),
    caminho("M112 4l1.8 4 4.2.6-3 3 .7 4.2-3.7-2-3.7 2 .7-4.2-3-3 4.2-.6z"),
)

/** O sistema pediu menos movimento ("Remover animações" na acessibilidade). */
@Composable
fun menosMovimento(): Boolean {
    val contexto = LocalContext.current
    return remember(contexto) {
        Settings.Global.getFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * O porquinho das telas de erro e de abertura, desenhado como o SVG do site (viewBox 220×180).
 * Flutua, a moeda rola e as estrelas piscam; parado quando o sistema pede menos movimento.
 * É enfeite: o TalkBack não lê.
 */
@Composable
fun Porquinho(humor: HumorDoPorquinho, modifier: Modifier = Modifier, largura: Dp = 180.dp) {
    val cores = CoresDoPorquinho(MaterialTheme.colorScheme, isSystemInDarkTheme())
    val parado = menosMovimento()
    val animacao = rememberInfiniteTransition(label = "porquinho")
    val flutua = animar(animacao, parado, 3200)
    val rola = animar(animacao, parado, 2600)
    val pisca = animar(animacao, parado, 1600)
    val texto = rememberTextMeasurer()
    val estiloDoSinal = TextStyle(color = MaterialTheme.colorScheme.primary, fontSize = 34.sp, fontWeight = FontWeight.Bold)

    Canvas(modifier.size(largura, largura * 180f / 220f)) {
        scale(size.width / 220f, size.height / 180f, pivot = Offset.Zero) {
            drawOval(cores.sombra, Offset(42f, 159f), Size(128f, 14f))
            if (humor == HumorDoPorquinho.Perdido) {
                translate(left = 8f * rola.value) {
                    rotate(90f * rola.value, pivot = Offset(196f, 152f)) {
                        drawCircle(cores.ouro, 12f, Offset(196f, 152f))
                        drawCircle(cores.ouroEscuro, 12f, Offset(196f, 152f), style = Stroke(2.5f))
                        drawCircle(cores.ouroEscuro.copy(alpha = 0.6f), 7f, Offset(196f, 152f), style = Stroke(2f))
                    }
                }
                drawText(texto, "?", Offset(170f, 8f), estiloDoSinal)
                drawText(texto, "?", Offset(192f, 40f), estiloDoSinal.copy(fontSize = 22.sp, color = estiloDoSinal.color.copy(alpha = 0.7f)))
            }
            if (humor == HumorDoPorquinho.Tonto) {
                ESTRELAS.forEachIndexed { indice, estrela ->
                    val fase = (pisca.value + indice / 3f) % 1f
                    drawPath(estrela, cores.ouro, alpha = if (parado) 1f else 1f - 0.65f * fase)
                }
            }
            translate(top = -4f * flutua.value) { corpo(cores, humor) }
        }
    }
}

@Composable
private fun animar(animacao: InfiniteTransition, parado: Boolean, duracao: Int): State<Float> =
    if (parado) remember { mutableFloatStateOf(0f) } else animacao.animateFloat(
        0f, 1f, infiniteRepeatable(tween(duracao / 2), RepeatMode.Reverse), label = "porquinho",
    )

private fun DrawScope.corpo(cores: CoresDoPorquinho, humor: HumorDoPorquinho) {
    val traco = Stroke(3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(RABO, cores.contorno, style = traco)
    for (x in listOf(58f, 124f)) {
        drawRoundRect(cores.rosa, Offset(x, 126f), Size(22f, 34f), CornerRadius(10f))
        drawRoundRect(cores.contorno, Offset(x, 126f), Size(22f, 34f), CornerRadius(10f), style = traco)
    }
    drawPath(ORELHA_DE_TRAS, cores.contorno)
    drawPath(ORELHA_DE_TRAS, cores.contorno, style = traco)
    drawOval(cores.corpo, Offset(36f, 50f), Size(132f, 100f))
    drawOval(cores.contorno, Offset(36f, 50f), Size(132f, 100f), style = traco)
    drawRoundRect(cores.contorno, Offset(76f, 57f), Size(32f, 8f), CornerRadius(4f))
    drawPath(ORELHA, cores.rosa)
    drawPath(ORELHA, cores.contorno, style = traco)
    drawOval(cores.rosa.copy(alpha = 0.55f), Offset(127f, 105.5f), Size(18f, 11f))
    drawOval(cores.rosa, Offset(150f, 86f), Size(32f, 26f))
    drawOval(cores.contorno, Offset(150f, 86f), Size(32f, 26f), style = traco)
    drawOval(cores.contorno, Offset(157.4f, 95f), Size(5.2f, 8f))
    drawOval(cores.contorno, Offset(168.4f, 95f), Size(5.2f, 8f))

    when (humor) {
        HumorDoPorquinho.Tonto -> {
            drawPath(OLHO_X, cores.contorno, style = traco)
            drawPath(BOCA_TONTA, cores.contorno, style = traco)
            rotate(-28f, pivot = Offset(78f, 84f)) {
                drawRoundRect(cores.ouro, Offset(60f, 77f), Size(36f, 14f), CornerRadius(7f))
                drawRoundRect(cores.ouroEscuro, Offset(60f, 77f), Size(36f, 14f), CornerRadius(7f), style = Stroke(2f))
                drawRect(cores.ouroEscuro.copy(alpha = 0.3f), Offset(72f, 77f), Size(12f, 14f))
            }
        }
        HumorDoPorquinho.Perdido -> {
            drawPath(SOBRANCELHA, cores.contorno, style = traco)
            drawCircle(cores.olho, 6f, Offset(136f, 85f))
            drawCircle(cores.brilho, 2f, Offset(137.5f, 81.5f))
            drawOval(cores.contorno, Offset(149.5f, 116.5f), Size(7f, 9f))
        }
        HumorDoPorquinho.Feliz -> {
            drawCircle(cores.olho, 6f, Offset(136f, 86f))
            drawCircle(cores.brilho, 2f, Offset(138f, 84f))
            drawPath(SORRISO, cores.contorno, style = traco)
        }
    }
}
