package br.com.piggu.feature.relatorios

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.model.RelatorioDoAno
import br.com.piggu.core.model.ResumoDoMes
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.AvisoDeGuardado
import br.com.piggu.core.ui.BarraDeProgresso
import br.com.piggu.core.ui.EstadoDeErro
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.FalhaAoCarregar
import br.com.piggu.core.ui.SeletorDeMes
import br.com.piggu.core.ui.Vazio
import br.com.piggu.core.ui.formatos.formatarMoeda
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

data class EventosDeRelatorios(
    val abrirPremium: () -> Unit = {},
    val lancarGasto: () -> Unit = {},
    val mudarMes: (String) -> Unit = {},
    val tentarDeNovo: () -> Unit = {},
    val alternarTabelaDoMes: () -> Unit = {},
    val alternarTabelaDoAno: () -> Unit = {},
    val focarMes: (Int?) -> Unit = {},
    val exportar: () -> Unit = {},
)

/**
 * A aba Relatórios ligada ao [RelatoriosViewModel], dentro da moldura (sem topo próprio).
 * [abrirPremium] leva ao plano; [lancarGasto] abre a folha do + quando o mês está vazio.
 */
@Composable
fun TelaDeRelatorios(
    abrirPremium: () -> Unit,
    lancarGasto: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RelatoriosViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    LaunchedEffect(estado.csv) {
        val csv = estado.csv ?: return@LaunchedEffect
        compartilhar(contexto, csv)
        viewModel.csvEntregue()
    }
    TelaDeRelatorios(
        estado,
        EventosDeRelatorios(
            abrirPremium = abrirPremium,
            lancarGasto = lancarGasto,
            mudarMes = viewModel::mudarMes,
            tentarDeNovo = viewModel::tentarDeNovo,
            alternarTabelaDoMes = viewModel::alternarTabelaDoMes,
            alternarTabelaDoAno = viewModel::alternarTabelaDoAno,
            focarMes = viewModel::focarMes,
            exportar = viewModel::exportar,
        ),
        modifier,
    )
}

/** Grava o CSV na pasta de cache e abre o compartilhamento do Android, pelo FileProvider do módulo. */
internal suspend fun compartilhar(contexto: Context, csv: CsvDoMes) {
    val arquivo = withContext(Dispatchers.IO) {
        File(contexto.cacheDir, "relatorios").apply { mkdirs() }.resolve(csv.nome).apply { writeBytes(csv.bytes) }
    }
    val endereco = FileProvider.getUriForFile(contexto, "${contexto.packageName}.relatorios.arquivos", arquivo)
    val envio = Intent(Intent.ACTION_SEND)
        .setType("text/csv")
        .putExtra(Intent.EXTRA_STREAM, endereco)
        .putExtra(Intent.EXTRA_TITLE, csv.nome)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    contexto.startActivity(Intent.createChooser(envio, contexto.getString(R.string.relatorios_compartilhar)))
}

/** O FileProvider do CSV exportado (AndroidManifest.xml deste módulo). */
class ArquivosDosRelatorios : FileProvider()

/** A aba só desenhada (capturas e prévias). */
@Composable
fun TelaDeRelatorios(estado: EstadoDeRelatorios, eventos: EventosDeRelatorios, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().wrapContentWidth().widthIn(max = 720.dp)) {
        SeletorDeMes(estado.mes, estado.mesAtual, eventos.mudarMes, Modifier.align(Alignment.CenterHorizontally))
        estado.erro?.let { erro ->
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    erro.texto(LocalContext.current),
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp).semantics { liveRegion = LiveRegionMode.Assertive },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        estado.guardadoEm?.let { AvisoDeGuardado(it, estado.conectado) }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "mes") {
                when (val carga = estado.resumo) {
                    Carga.Carregando -> Esqueleto(linhas = 5)
                    is Carga.Falhou -> Falha(carga.erro, eventos.tentarDeNovo)
                    is Carga.Pronto -> CartaoDoMes(carga.valor, estado, eventos)
                }
            }
            item(key = "exportar") {
                OutlinedButton(eventos.exportar, Modifier.fillMaxWidth(), enabled = estado.podeExportar) {
                    Text(stringResource(R.string.relatorios_exportar))
                }
            }
            item(key = "ano") { CartaoDoAno(estado, eventos) }
            estado.anoPronto?.takeIf { estado.ehPremium && it.porCategoria.isNotEmpty() }?.let { ano ->
                item(key = "para-onde") { ParaOndeFoi(ano, estado.moeda) }
            }
        }
    }
}

@Composable
private fun Falha(erro: ErroDaApi, tentarDeNovo: () -> Unit) {
    val falha = when {
        erro is ErroDaApi.SemInternet -> FalhaAoCarregar.SemInternet
        erro is ErroDaApi.DoServidor && erro.status >= 500 -> FalhaAoCarregar.DoServidor
        else -> FalhaAoCarregar.Outra
    }
    EstadoDeErro(falha, tentarDeNovo, mensagem = mensagemDeErro(erro).texto(LocalContext.current))
}

@Composable
private fun Cartao(conteudo: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = conteudo)
    }
}

@Composable
private fun Titulo(texto: String, modifier: Modifier = Modifier) =
    Text(texto, modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)

/** Rótulo à esquerda, valor à direita, lidos juntos (LabeledContent do iOS). */
@Composable
private fun Numero(rotulo: String, valor: String, detalhe: String? = null, corDoValor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        Text(rotulo, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(horizontalAlignment = Alignment.End) {
            Text(valor, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = corDoValor)
            detalhe?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Receitas em verde e gastos na cor do tema, o par das séries do site (--serie-receita, --serie-gasto). */
@Composable
private fun corDaReceita(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF3EE08F) else Color(0xFF22B573)

@Composable
private fun corDoGasto(): Color = MaterialTheme.colorScheme.primary

/** "▲ 12,5% a mais", como o number: '1.0-1' do site. */
@Composable
private fun textoDaVariacao(variacao: BigDecimal): String {
    val numero = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).apply { maximumFractionDigits = 1 }
    return when (variacao.signum()) {
        1 -> stringResource(R.string.relatorios_a_mais, numero.format(variacao))
        -1 -> stringResource(R.string.relatorios_a_menos, numero.format(variacao.negate()))
        else -> stringResource(R.string.relatorios_igual)
    }
}

@Composable
private fun Amostra(cor: Color, texto: String, traco: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(if (traco) 2.dp else 10.dp, if (traco) 14.dp else 10.dp).background(cor, if (traco) MaterialTheme.shapes.extraSmall else CircleShape))
        Text(texto, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CartaoDoMes(resumo: ResumoDoMes, estado: EstadoDeRelatorios, eventos: EventosDeRelatorios) {
    val moeda = estado.moeda
    Cartao {
        Numero(stringResource(R.string.relatorios_gastos), formatarMoeda(resumo.gastos, moeda))
        Numero(
            stringResource(R.string.relatorios_mes_anterior),
            formatarMoeda(resumo.gastosMesAnterior, moeda),
            resumo.variacao?.let { textoDaVariacao(it) },
        )
        resumo.projecaoDeGastos?.let { Numero(stringResource(R.string.relatorios_projecao), formatarMoeda(it, moeda)) }
        if (resumo.porCategoria.isEmpty()) {
            Vazio(
                Icone.Grafico,
                stringResource(R.string.relatorios_vazio_titulo),
                texto = stringResource(R.string.relatorios_vazio_texto),
                compacto = true,
                acao = stringResource(R.string.relatorios_vazio_acao) to eventos.lancarGasto,
            )
            return@Cartao
        }
        HorizontalDivider()
        val gasto = corDoGasto()
        val marca = MaterialTheme.colorScheme.onSurfaceVariant
        Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Amostra(gasto, stringResource(R.string.relatorios_este_mes))
            Amostra(marca, stringResource(R.string.relatorios_mes_anterior), traco = true)
        }
        resumo.porCategoria.forEach { c ->
            val descricao = stringResource(
                R.string.relatorios_categoria_descricao,
                c.categoria,
                formatarMoeda(c.total, moeda),
                formatarMoeda(c.anterior, moeda),
            )
            Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = descricao }) {
                Row(Modifier.fillMaxWidth()) {
                    Text(c.categoria, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(formatarMoeda(c.total, moeda), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                BarraComMarca(
                    estado.fracaoDoMes(c.total),
                    estado.fracaoDoMes(c.anterior).takeIf { c.anterior.signum() > 0 },
                    gasto,
                    marca,
                )
            }
        }
        BotaoDaTabela(estado.tabelaDoMes, eventos.alternarTabelaDoMes)
        if (estado.tabelaDoMes) {
            Tabela(
                listOf(stringResource(R.string.relatorios_categoria), stringResource(R.string.relatorios_este_mes), stringResource(R.string.relatorios_mes_anterior)),
                resumo.porCategoria.map { listOf(it.categoria, formatarMoeda(it.total, moeda), formatarMoeda(it.anterior, moeda)) },
            )
        }
    }
}

/** A barra deste mês e o traço do mês anterior por cima (.trilho, .barra-gasto e .marca-anterior do site). */
@Composable
private fun BarraComMarca(fracao: Float, anterior: Float?, cor: Color, corDaMarca: Color) {
    val trilho = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        val altura = 10.dp.toPx()
        val topo = (size.height - altura) / 2
        val raio = CornerRadius(altura / 2)
        drawRoundRect(trilho, Offset(0f, topo), Size(size.width, altura), raio)
        if (fracao > 0f) drawRoundRect(cor, Offset(0f, topo), Size(size.width * fracao, altura), raio)
        anterior?.let {
            val x = (size.width * it).coerceIn(1.dp.toPx(), size.width - 1.dp.toPx())
            drawLine(corDaMarca, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
        }
    }
}

@Composable
private fun BotaoDaTabela(aberta: Boolean, alternar: () -> Unit) {
    TextButton(alternar) {
        Text(stringResource(if (aberta) R.string.relatorios_esconder_tabela else R.string.relatorios_ver_em_tabela))
    }
}

/** Tabela simples: a primeira coluna é o rótulo da linha; cada linha é lida de uma vez pelo TalkBack. */
@Composable
private fun Tabela(cabecalho: List<String>, linhas: List<List<String>>) {
    @Composable
    fun Linha(celulas: List<String>, negrito: Boolean, modifier: Modifier = Modifier) {
        Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            celulas.forEachIndexed { i, celula ->
                Text(
                    celula,
                    Modifier.weight(if (i == 0) 0.8f else 1f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (negrito || i == 0) FontWeight.SemiBold else null,
                    textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                )
            }
        }
    }
    Column {
        Linha(cabecalho, negrito = true, Modifier.semantics { heading() })
        HorizontalDivider()
        linhas.forEach { celulas ->
            // "Set: Receitas R$ 1,00, Gastos R$ 2,00...": o cabeçalho junto de cada valor.
            val lida = celulas.first() + ": " + cabecalho.drop(1).zip(celulas.drop(1)) { titulo, valor -> "$titulo $valor" }.joinToString(", ")
            Linha(celulas, negrito = false, Modifier.clearAndSetSemantics { contentDescription = lida })
        }
    }
}

@Composable
private fun CartaoDoAno(estado: EstadoDeRelatorios, eventos: EventosDeRelatorios) {
    Cartao {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Titulo(stringResource(R.string.relatorios_ano_de, estado.ano), Modifier.weight(1f))
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(Icone.Estrela.desenho), null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        stringResource(R.string.relatorios_premium),
                        Modifier.padding(start = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        if (!estado.ehPremium) {
            Text(stringResource(R.string.relatorios_convite), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(eventos.abrirPremium) { Text(stringResource(R.string.relatorios_conhecer_premium)) }
            return@Cartao
        }
        when (val carga = estado.doAno) {
            null, Carga.Carregando -> Esqueleto(linhas = 3)
            is Carga.Falhou -> Falha(carga.erro, eventos.tentarDeNovo)
            is Carga.Pronto -> DoAno(carga.valor, estado, eventos)
        }
    }
}

@Composable
private fun ColumnScope.DoAno(ano: RelatorioDoAno, estado: EstadoDeRelatorios, eventos: EventosDeRelatorios) {
    val moeda = estado.moeda
    val meses = stringArrayResource(R.array.relatorios_meses_curtos)
    val receita = corDaReceita()
    val gasto = corDoGasto()
    Numero(stringResource(R.string.relatorios_receitas), formatarMoeda(ano.receitas, moeda))
    Numero(stringResource(R.string.relatorios_gastos), formatarMoeda(ano.gastos, moeda))
    Numero(
        stringResource(R.string.relatorios_sobra),
        formatarMoeda(ano.sobra, moeda),
        corDoValor = if (ano.sobra.signum() < 0) MaterialTheme.colorScheme.error else Color.Unspecified,
    )
    Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Amostra(receita, stringResource(R.string.relatorios_receitas))
        Amostra(gasto, stringResource(R.string.relatorios_gastos))
    }
    GraficoDoAno(estado, receita, gasto, stringResource(R.string.relatorios_grafico_do_ano, ano.ano), eventos.focarMes)
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
        meses.forEach {
            Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
        }
    }
    val emFoco = estado.mesEmFoco
    val m = emFoco?.let { ano.meses.getOrNull(it) }
    if (emFoco != null && m != null) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(12.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text(stringResource(R.string.relatorios_dica_titulo, meses[emFoco], ano.ano), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.relatorios_dica_receitas, formatarMoeda(m.receitas, moeda)), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.relatorios_dica_gastos, formatarMoeda(m.gastos, moeda)), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.relatorios_dica_sobra, formatarMoeda(m.sobra, moeda)), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    BotaoDaTabela(estado.tabelaDoAno, eventos.alternarTabelaDoAno)
    if (estado.tabelaDoAno) {
        Tabela(
            listOf(
                stringResource(R.string.relatorios_mes),
                stringResource(R.string.relatorios_receitas),
                stringResource(R.string.relatorios_gastos),
                stringResource(R.string.relatorios_sobra),
            ),
            ano.meses.mapIndexed { i, m ->
                listOf(meses.getOrElse(i) { m.mes }, formatarMoeda(m.receitas, moeda), formatarMoeda(m.gastos, moeda), formatarMoeda(m.sobra, moeda))
            },
        )
    }
}

/** As 12 colunas, receita e gasto lado a lado, com o pé reto na base (o SVG do site). Tocar mostra a dica do mês. */
@Composable
private fun GraficoDoAno(estado: EstadoDeRelatorios, receita: Color, gasto: Color, descricao: String, focarMes: (Int?) -> Unit) {
    val meses = estado.anoPronto?.meses.orEmpty()
    val foco = MaterialTheme.colorScheme.primaryContainer
    val base = MaterialTheme.colorScheme.outlineVariant
    val focar by rememberUpdatedState(focarMes)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = descricao }
            .pointerInput(Unit) { detectTapGestures { focar((it.x / (size.width / 12f)).toInt().coerceIn(0, 11)) } },
    ) {
        val passo = size.width / 12
        val largura = minOf(14.dp.toPx(), passo / 2 - 2.dp.toPx())
        val util = size.height - 10.dp.toPx()
        estado.mesEmFoco?.let { drawRect(foco, Offset(passo * it, 0f), Size(passo, size.height)) }
        drawLine(base, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        fun barra(x: Float, fracao: Float, cor: Color) {
            val altura = util * fracao
            if (altura <= 0f) return
            val raio = CornerRadius(minOf(4.dp.toPx(), altura, largura / 2))
            val caminho = Path().apply {
                addRoundRect(RoundRect(x, size.height - altura, x + largura, size.height, topLeftCornerRadius = raio, topRightCornerRadius = raio))
            }
            drawPath(caminho, cor)
        }
        meses.forEachIndexed { i, m ->
            val meio = passo * i + passo / 2
            barra(meio - largura - 1.dp.toPx(), estado.fracaoDoAno(m.receitas), receita)
            barra(meio + 1.dp.toPx(), estado.fracaoDoAno(m.gastos), gasto)
        }
    }
}

@Composable
private fun ParaOndeFoi(ano: RelatorioDoAno, moeda: String?) {
    val maior = ano.porCategoria.first().total.toDouble().coerceAtLeast(1.0)
    Cartao {
        Titulo(stringResource(R.string.relatorios_para_onde_foi))
        ano.porCategoria.forEach { c ->
            val total = formatarMoeda(c.total, moeda)
            Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "${c.categoria}: $total" }) {
                Row(Modifier.fillMaxWidth()) {
                    Text(c.categoria, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(total, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                BarraDeProgresso((c.total.toDouble() / maior).toFloat(), Modifier.padding(top = 4.dp),cor = corDoGasto())
            }
        }
    }
}
