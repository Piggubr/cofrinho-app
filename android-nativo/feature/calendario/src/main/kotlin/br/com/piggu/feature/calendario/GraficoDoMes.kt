package br.com.piggu.feature.calendario

import android.icu.text.CompactDecimalFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import br.com.piggu.core.ui.Vazio
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.ui.formatos.formatarMoeda
import br.com.piggu.core.ui.formatos.simboloDaMoeda
import java.math.BigDecimal
import java.util.Locale

private val ALTURA = 190.dp
private val TOPO = 18.dp
private val RODAPE = 22.dp
private val MARGEM = 10.dp

/** Dias com rótulo no eixo: o começo de cada semana (e o último dia, somado na hora). */
private val DIAS_NO_EIXO = listOf(1, 8, 15, 22)

/** As cores das séries do site (--serie-receita; o gasto acompanha a cor do tema). */
@Composable
internal fun corDaReceita(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF3EE08F) else Color(0xFF22B573)

@Composable
internal fun corDoGasto(): Color = MaterialTheme.colorScheme.primary

/** "6,5 mil", "412,3": valor curto para a grade e o eixo, como o compactName do iOS. */
internal fun compacto(valor: BigDecimal): String =
    CompactDecimalFormat.getInstance(Locale.forLanguageTag("pt-BR"), CompactDecimalFormat.CompactStyle.SHORT).apply {
        setSignificantDigitsUsed(false)
        maximumFractionDigits = 1
    }.format(valor)

/**
 * Receitas e gastos do mês, acumulados dia a dia (grafico-do-mes.ts), desenhados no Canvas. A
 * distância entre as linhas é a sobra. Tocar ou arrastar mostra o dia; o dia aberto na grade fica
 * marcado. O TalkBack anda pelos dias nas ações "Dia anterior" e "Próximo dia", e a tabela por
 * semana é a versão em texto.
 */
@Composable
internal fun GraficoDoMes(estado: EstadoDoCalendario, apontar: (Int?) -> Unit) {
    if (estado.semMovimento) {
        Vazio(Icone.Grafico, stringResource(R.string.calendario_sem_movimento), compacto = true)
        return
    }
    val ultimo = estado.serie.last()
    val receita = corDaReceita()
    val gasto = corDoGasto()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Numero(stringResource(R.string.calendario_receitas), formatarMoeda(ultimo.receitasAteAqui, estado.moeda), Modifier.weight(1f))
            Numero(stringResource(R.string.calendario_gastos), formatarMoeda(ultimo.gastosAteAqui, estado.moeda), Modifier.weight(1f))
            Numero(
                stringResource(R.string.calendario_sobra),
                formatarMoeda(ultimo.sobra, estado.moeda),
                Modifier.weight(1f),
                if (ultimo.sobra.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legenda(receita, stringResource(R.string.calendario_receitas_acumuladas))
            Legenda(gasto, stringResource(R.string.calendario_gastos_acumulados))
        }
        Desenho(estado, receita, gasto, apontar)
        TabelaPorSemana(estado)
    }
}

@Composable
private fun Numero(rotulo: String, valor: String, modifier: Modifier, cor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(rotulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = cor)
    }
}

@Composable
private fun Legenda(cor: Color, texto: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(cor, RoundedCornerShape(3.dp)))
        Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** O texto da dica do dia, usado também pelo TalkBack. */
@Composable
private fun linhasDaDica(dia: DiaDaSerie, moeda: String): List<String> = listOfNotNull(
    stringResource(R.string.calendario_ate_o_dia, dia.dia),
    stringResource(R.string.calendario_dica_receitas, formatarMoeda(dia.receitasAteAqui, moeda)),
    stringResource(R.string.calendario_dica_gastos, formatarMoeda(dia.gastosAteAqui, moeda)),
    stringResource(R.string.calendario_dica_sobra, formatarMoeda(dia.sobra, moeda)),
    if (dia.receitas.signum() > 0) stringResource(R.string.calendario_entrou_no_dia, formatarMoeda(dia.receitas, moeda)) else null,
    if (dia.gastos.signum() > 0) stringResource(R.string.calendario_saiu_no_dia, formatarMoeda(dia.gastos, moeda)) else null,
)

@Composable
private fun Desenho(estado: EstadoDoCalendario, receita: Color, gasto: Color, apontar: (Int?) -> Unit) {
    val medidor = rememberTextMeasurer()
    val estiloDoEixo = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val linhaDeGrade = MaterialTheme.colorScheme.outlineVariant
    val cursor = MaterialTheme.colorScheme.outline
    val superficie = MaterialTheme.colorScheme.surfaceContainerLow
    val simbolo = simboloDaMoeda(estado.moeda)
    val serie = estado.serie
    val diasNoMes = estado.diasNoMes
    val destaque = estado.destaque
    val topo = topoDaEscala(maxOf(serie.last().receitasAteAqui, serie.last().gastosAteAqui).toDouble())
    val dica = destaque?.let { linhasDaDica(it, estado.moeda) }
    val atual by rememberUpdatedState(estado.diaNoGrafico ?: serie.size)
    val aoApontar by rememberUpdatedState(apontar)
    val total = serie.size
    fun apontarNoX(x: Float, largura: Float, margem: Float) = aoApontar(diaNoPonto((x - margem) / (largura - 2 * margem), diasNoMes, total))
    val descricao = stringResource(R.string.calendario_grafico_descricao)
    val anterior = stringResource(R.string.calendario_dia_anterior)
    val proximo = stringResource(R.string.calendario_proximo_dia)

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(ALTURA)
            .semantics {
                contentDescription = descricao
                dica?.let { stateDescription = it.joinToString(", ") }
                customActions = listOf(
                    CustomAccessibilityAction(anterior) { apontar(atual - 1); true },
                    CustomAccessibilityAction(proximo) { apontar(atual + 1); true },
                )
            },
    ) {
        Canvas(
            Modifier
                .matchParentSize()
                .pointerInput(diasNoMes, serie.size) {
                    detectTapGestures { apontarNoX(it.x, size.width.toFloat(), MARGEM.toPx()) }
                }
                .pointerInput(diasNoMes, serie.size) {
                    detectHorizontalDragGestures { mudanca, _ -> apontarNoX(mudanca.position.x, size.width.toFloat(), MARGEM.toPx()) }
                },
        ) {
            val margem = MARGEM.toPx()
            val base = size.height - RODAPE.toPx()
            val alto = TOPO.toPx()
            fun x(dia: Int) = margem + (dia - 1f) / (diasNoMes - 1f) * (size.width - 2 * margem)
            fun y(valor: BigDecimal) = base - (valor.toFloat() / topo.toFloat()) * (base - alto)

            for (valor in listOf(topo / 2, topo)) {
                val altura = y(BigDecimal(valor))
                drawLine(linhaDeGrade, Offset(0f, altura), Offset(size.width, altura), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())))
                val rotulo = medidor.measure("$simbolo ${compacto(BigDecimal(valor))}", estiloDoEixo)
                drawText(rotulo, topLeft = Offset(0f, altura - rotulo.size.height - 2.dp.toPx()))
            }
            drawLine(linhaDeGrade, Offset(0f, base), Offset(size.width, base), 1.dp.toPx())
            for (dia in DIAS_NO_EIXO + diasNoMes) {
                val rotulo = medidor.measure(dia.toString(), estiloDoEixo)
                val esquerda = if (dia == diasNoMes) x(dia) - rotulo.size.width else x(dia) - rotulo.size.width / 2
                drawText(rotulo, topLeft = Offset(esquerda.coerceAtLeast(0f), size.height - rotulo.size.height))
            }
            destaque?.let { drawLine(cursor, Offset(x(it.dia), 0f), Offset(x(it.dia), base), 1.dp.toPx()) }
            for ((cor, valor) in listOf(receita to DiaDaSerie::receitasAteAqui, gasto to DiaDaSerie::gastosAteAqui)) {
                val caminho = Path()
                serie.forEachIndexed { i, d -> if (i == 0) caminho.moveTo(x(d.dia), y(valor(d))) else caminho.lineTo(x(d.dia), y(valor(d))) }
                drawPath(caminho, cor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                // Um mês de um dia só não tem linha: o ponto mostra o valor.
                if (serie.size == 1) drawCircle(cor, 3.dp.toPx(), Offset(x(1), y(valor(serie[0]))))
            }
            destaque?.let { d ->
                for ((cor, valor) in listOf(receita to d.receitasAteAqui, gasto to d.gastosAteAqui)) {
                    // Anel da cor da superfície para o ponto se destacar da linha que cruza.
                    drawCircle(superficie, 6.5.dp.toPx(), Offset(x(d.dia), y(valor)))
                    drawCircle(cor, 4.5.dp.toPx(), Offset(x(d.dia), y(valor)))
                }
            }
        }
        if (destaque != null && estado.diaNoGrafico != null && dica != null) {
            val largura = constraints.maxWidth
            val margem = with(LocalDensity.current) { MARGEM.toPx() }
            val xDoDia = margem + (destaque.dia - 1f) / (diasNoMes - 1f) * (largura - 2 * margem)
            Dica(
                dica,
                Modifier.layout { medida, limites ->
                    val colocavel = medida.measure(limites.copy(minWidth = 0))
                    val afastado = 10.dp.roundToPx()
                    // A dica fica ao lado do dia, sem cobrir as linhas que já passaram.
                    val esquerda = if (xDoDia > largura / 2) xDoDia.toInt() - colocavel.width - afastado else xDoDia.toInt() + afastado
                    layout(colocavel.width, colocavel.height) { colocavel.place(esquerda.coerceIn(0, maxOf(0, largura - colocavel.width)), 0) }
                },
            )
        }
    }
}

@Composable
private fun Dica(linhas: List<String>, modifier: Modifier) {
    Surface(
        // O TalkBack já lê o dia pelo stateDescription do gráfico.
        modifier.clearAndSetSemantics {},
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            linhas.forEachIndexed { i, linha ->
                Text(
                    linha,
                    style = if (i == 0) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                    fontWeight = if (i == 0) FontWeight.SemiBold else null,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A tabela por semana, versão em texto do gráfico: cada linha é lida inteira pelo TalkBack. */
@Composable
private fun TabelaPorSemana(estado: EstadoDoCalendario) {
    var aberta by rememberSaveable { mutableStateOf(false) }
    TextButton({ aberta = !aberta }, Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(if (aberta) R.string.calendario_esconder_semanas else R.string.calendario_ver_por_semana))
    }
    if (!aberta) return
    val estilo = MaterialTheme.typography.bodySmall
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            for (titulo in listOf(R.string.calendario_semana_dias, R.string.calendario_receitas, R.string.calendario_gastos, R.string.calendario_sobra)) {
                Text(stringResource(titulo), Modifier.weight(1f), style = estilo, fontWeight = FontWeight.SemiBold)
            }
        }
        estado.semanas.forEach { semana ->
            HorizontalDivider()
            val receitas = formatarMoeda(semana.receitas, estado.moeda)
            val gastos = formatarMoeda(semana.gastos, estado.moeda)
            val sobra = formatarMoeda(semana.sobra, estado.moeda)
            val lida = stringResource(R.string.calendario_semana_descricao, semana.de, semana.ate, receitas, gastos, sobra)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).clearAndSetSemantics { contentDescription = lida }) {
                Text(stringResource(R.string.calendario_semana, semana.de, semana.ate), Modifier.weight(1f), style = estilo)
                Text(receitas, Modifier.weight(1f), style = estilo)
                Text(gastos, Modifier.weight(1f), style = estilo)
                Text(
                    sobra,
                    Modifier.weight(1f),
                    style = estilo,
                    color = if (semana.sobra.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
