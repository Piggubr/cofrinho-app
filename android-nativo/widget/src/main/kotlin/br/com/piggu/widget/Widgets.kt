package br.com.piggu.widget

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.Row
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import br.com.piggu.core.designsystem.VarianteDeCor
import br.com.piggu.core.designsystem.esquema
import br.com.piggu.core.ui.formatos.formatarMoeda
import kotlinx.coroutines.flow.first
import java.time.YearMonth
import br.com.piggu.core.designsystem.R as Desenhos

private val PEQUENO = DpSize(110.dp, 110.dp)
private val MEDIO = DpSize(250.dp, 110.dp)

/**
 * Os widgets do Piggu (PigguWidgets.swift do iOS): o gasto do mês contra o limite, o progresso de
 * uma meta e o "Lançar gasto". Leem só o [ResumoDoWidget] que o app grava; nunca chamam a API.
 */
abstract class WidgetDoPiggu : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(PEQUENO, MEDIO))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val guarda = GuardaDoResumo.de(context)
        val inicial = guarda.resumo.first()
        provideContent {
            // Lido na composição: um updateAll com a sessão do widget aberta redesenha com o resumo novo.
            val resumo by guarda.resumo.collectAsState(inicial)
            TemaDoWidget(resumo) {
                Cartao(link()) { Conteudo(resumo?.takeIf { it.mes == YearMonth.now().toString() }, resumo != null) }
            }
        }
    }

    /** O link que o toque abre no app (os do MainActivity). */
    protected abstract fun link(): String

    /**
     * @param resumo o do mês atual; nulo também quando é de um mês que passou (melhor não mostrar número velho).
     * @param entrou há resumo gravado, de qualquer mês: sem ele, a pessoa não entrou (ou saiu).
     */
    @Composable
    protected abstract fun ColumnScope.Conteudo(resumo: ResumoDoWidget?, entrou: Boolean)
}

class GastoDoMesWidget : WidgetDoPiggu() {
    override fun link() = "piggu://gastos"

    @Composable
    override fun ColumnScope.Conteudo(resumo: ResumoDoWidget?, entrou: Boolean) {
        Rotulo(R.string.widget_gasto_do_mes)
        if (resumo == null) return Aviso(if (entrou) R.string.widget_abra_o_piggu else R.string.widget_entre_no_piggu)
        val medio = LocalSize.current.width >= MEDIO.width
        Text(
            formatarMoeda(resumo.gasto, resumo.moeda),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = if (medio) 26.sp else 20.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        val porcentagem = resumo.porcentagemDoLimite
        val restante = resumo.restante
        if (porcentagem == null || resumo.limite == null || restante == null) {
            Detalhe(texto(R.string.widget_sem_limite))
        } else {
            val estourou = porcentagem > 100
            val cor = if (estourou) GlanceTheme.colors.error else GlanceTheme.colors.primary
            LinearProgressIndicator(
                progress = porcentagem.coerceAtMost(100) / 100f,
                modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                color = cor,
                backgroundColor = GlanceTheme.colors.surfaceVariant,
            )
            Spacer(GlanceModifier.height(4.dp))
            Detalhe(
                if (estourou) texto(R.string.widget_passou_do_limite, formatarMoeda(restante.negate(), resumo.moeda))
                else texto(R.string.widget_resta_do_limite, formatarMoeda(restante, resumo.moeda), formatarMoeda(resumo.limite, resumo.moeda)),
                if (estourou) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
            )
            if (medio) {
                val hora = DateFormat.getTimeFormat(LocalContext.current).format(resumo.atualizadoEm)
                Detalhe(texto(R.string.widget_porcentagem_e_hora, porcentagem, hora))
            }
        }
    }
}

class MetaDoMesWidget : WidgetDoPiggu() {
    override fun link() = "piggu://metas"

    @Composable
    override fun ColumnScope.Conteudo(resumo: ResumoDoWidget?, entrou: Boolean) {
        Rotulo(R.string.widget_meta_do_mes)
        val meta = resumo?.meta
        if (resumo == null || meta == null) {
            return Aviso(
                when {
                    !entrou -> R.string.widget_entre_no_piggu
                    resumo == null -> R.string.widget_abra_o_piggu
                    else -> R.string.widget_sem_meta
                },
            )
        }
        Text(meta.titulo, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 2)
        Spacer(GlanceModifier.defaultWeight())
        LinearProgressIndicator(
            progress = (meta.atual.toFloat() / meta.alvo.coerceAtLeast(1)).coerceIn(0f, 1f),
            modifier = GlanceModifier.fillMaxWidth().height(8.dp),
            color = GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.surfaceVariant,
        )
        Spacer(GlanceModifier.height(4.dp))
        Detalhe(texto(R.string.widget_progresso_da_meta, meta.atual, meta.alvo, resumo.metasConcluidas, resumo.metasTotal))
    }
}

/** Só o botão: abre o formulário de gasto do app. Não lê número nenhum. */
class LancarGastoWidget : WidgetDoPiggu() {
    override fun link() = "piggu://gasto"

    @Composable
    override fun ColumnScope.Conteudo(resumo: ResumoDoWidget?, entrou: Boolean) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                ImageProvider(Desenhos.drawable.ic_adicionar),
                contentDescription = null,
                modifier = GlanceModifier.size(36.dp).background(GlanceTheme.colors.primary).cornerRadius(18.dp).padding(6.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                texto(R.string.widget_lancar_gasto),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 2,
            )
        }
    }
}

class GastoDoMesReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GastoDoMesWidget()
}

class MetaDoMesReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MetaDoMesWidget()
}

class LancarGastoReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LancarGastoWidget()
}

/** A cor do Piggu escolhida no app, claro e escuro como o aparelho; com as Cores do celular, as do aparelho. */
@Composable
private fun TemaDoWidget(resumo: ResumoDoWidget?, conteudo: @Composable () -> Unit) {
    // Sem resumo (não entrou), o Chiclete padrão.
    if (resumo != null && resumo.cor == null) return GlanceTheme(content = conteudo)
    val variante = VarianteDeCor.deId(resumo?.cor)
    GlanceTheme(colors = ColorProviders(light = variante.esquema(escuro = false), dark = variante.esquema(escuro = true)), content = conteudo)
}

@Composable
private fun Cartao(link: String, conteudo: @Composable ColumnScope.() -> Unit) {
    val contexto = LocalContext.current
    val abrir = Intent(Intent.ACTION_VIEW, link.toUri()).setPackage(contexto.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    Column(
        GlanceModifier.fillMaxSize().appWidgetBackground().background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp).padding(12.dp).clickable(actionStartActivity(abrir)),
    ) { conteudo() }
}

@Composable
private fun Rotulo(@StringRes texto: Int) {
    Text(texto(texto), style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    Spacer(GlanceModifier.height(4.dp))
}

@Composable
private fun Aviso(@StringRes texto: Int) {
    Text(texto(texto), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp))
}

@Composable
private fun Detalhe(texto: String, cor: ColorProvider = GlanceTheme.colors.onSurfaceVariant) {
    Text(texto, style = TextStyle(color = cor, fontSize = 11.sp), maxLines = 2)
}

@Composable
private fun texto(@StringRes id: Int, vararg argumentos: Any): String = LocalContext.current.getString(id, *argumentos)
