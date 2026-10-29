package br.com.piggu.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Gráficos simples desenhados no Canvas (a barra .progresso e o anel das metas do site).
// O TalkBack lê a porcentagem pelo progressBarRangeInfo.

/** Barra de progresso arredondada; [fracao] de 0 a 1 (o que passar de 1 enche a barra). */
@Composable
fun BarraDeProgresso(
    fracao: Float,
    modifier: Modifier = Modifier,
    cor: Color = MaterialTheme.colorScheme.primary,
    trilho: Color = MaterialTheme.colorScheme.surfaceVariant,
    altura: Dp = 8.dp,
) {
    val cheio = fracao.coerceIn(0f, 1f)
    Canvas(modifier.fillMaxWidth().height(altura).semantics { progressBarRangeInfo = ProgressBarRangeInfo(cheio, 0f..1f) }) {
        val raio = CornerRadius(size.height / 2)
        drawRoundRect(trilho, cornerRadius = raio)
        if (cheio > 0f) drawRoundRect(cor, size = Size(size.width * cheio, size.height), cornerRadius = raio)
    }
}

/** Anel de progresso, com o [centro] no meio (o anel das metas do site). */
@Composable
fun AnelDeProgresso(
    fracao: Float,
    modifier: Modifier = Modifier,
    lado: Dp = 88.dp,
    espessura: Dp = 10.dp,
    cor: Color = MaterialTheme.colorScheme.primary,
    trilho: Color = MaterialTheme.colorScheme.surfaceVariant,
    centro: @Composable () -> Unit = {},
) {
    val cheio = fracao.coerceIn(0f, 1f)
    Box(modifier.size(lado).semantics { progressBarRangeInfo = ProgressBarRangeInfo(cheio, 0f..1f) }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val traco = espessura.toPx()
            val area = Size(size.width - traco, size.height - traco)
            val canto = Offset(traco / 2, traco / 2)
            drawArc(trilho, 0f, 360f, false, canto, area, style = Stroke(traco))
            if (cheio > 0f) drawArc(cor, -90f, 360f * cheio, false, canto, area, style = Stroke(traco, cap = StrokeCap.Round))
        }
        centro()
    }
}
