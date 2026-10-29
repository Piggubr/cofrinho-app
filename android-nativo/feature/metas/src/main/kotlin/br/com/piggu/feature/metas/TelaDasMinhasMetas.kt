package br.com.piggu.feature.metas

import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.model.MetaDoMes
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.AvisoDeGuardado
import br.com.piggu.core.ui.BarraDeProgresso
import br.com.piggu.core.ui.EstadoDeErro
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.FalhaAoCarregar
import br.com.piggu.core.ui.HumorDoPorquinho
import br.com.piggu.core.ui.Porquinho
import br.com.piggu.core.ui.Vazio
import br.com.piggu.core.ui.formatos.mesPorExtenso
import br.com.piggu.core.ui.menosMovimento
import br.com.piggu.core.ui.R as Comum

data class EventosDasMinhasMetas(
    val voltar: () -> Unit = {},
    val tentarDeNovo: () -> Unit = {},
    /** Abre o compartilhamento do Android com o texto pronto. */
    val compartilhar: (String) -> Unit = {},
    val abrirNova: () -> Unit = {},
    val editar: (MetaDoMes) -> Unit = {},
    val fecharFormulario: () -> Unit = {},
    val mudarTitulo: (String) -> Unit = {},
    val mudarAlvo: (String) -> Unit = {},
    val mudarAtual: (String) -> Unit = {},
    val salvar: () -> Unit = {},
    val avancar: (MetaDoMes) -> Unit = {},
    val pedirParaApagar: (MetaDoMes) -> Unit = {},
    val confirmar: () -> Unit = {},
    val desistir: () -> Unit = {},
)

/** Minhas metas ligada ao [MinhasMetasViewModel]: tela cheia, aberta pelo Mais. */
@Composable
fun TelaDasMinhasMetas(voltar: () -> Unit, modifier: Modifier = Modifier, viewModel: MinhasMetasViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val tituloDoMenu = stringResource(R.string.minhasmetas_titulo)
    TelaDasMinhasMetas(
        estado,
        EventosDasMinhasMetas(
            voltar = voltar,
            tentarDeNovo = viewModel::tentarDeNovo,
            compartilhar = { texto ->
                val envio = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
                contexto.startActivity(Intent.createChooser(envio, tituloDoMenu))
            },
            abrirNova = viewModel::abrirNova,
            editar = viewModel::editar,
            fecharFormulario = viewModel::fecharFormulario,
            mudarTitulo = viewModel::mudarTitulo,
            mudarAlvo = viewModel::mudarAlvo,
            mudarAtual = viewModel::mudarAtual,
            salvar = viewModel::salvar,
            avancar = viewModel::avancar,
            pedirParaApagar = viewModel::pedirParaApagar,
            confirmar = viewModel::confirmar,
            desistir = viewModel::desistir,
        ),
        modifier,
    )
}

/** A tela só desenhada (capturas e prévias). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDasMinhasMetas(estado: EstadoDasMetas, eventos: EventosDasMinhasMetas, modifier: Modifier = Modifier) {
    val cabecalho = stringResource(R.string.metas_compartilhar_cabecalho, mesPorExtenso(estado.mes))
    val linha = stringResource(R.string.metas_compartilhar_linha)
    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.minhasmetas_titulo)) },
                navigationIcon = {
                    IconButton(eventos.voltar) { Icon(painterResource(Icone.Esquerda.desenho), stringResource(Comum.string.voltar)) }
                },
                actions = {
                    IconButton({ eventos.compartilhar(estado.textoParaCompartilhar(cabecalho, linha)) }, enabled = estado.lista.isNotEmpty()) {
                        Icon(painterResource(Icone.Compartilhar.desenho), stringResource(R.string.metas_compartilhar))
                    }
                },
            )
        },
    ) { margens ->
        Column(Modifier.padding(margens).fillMaxSize().wrapContentWidth().widthIn(max = 720.dp)) {
            estado.erro?.let { Faixa(it.texto(), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) }
            estado.guardadoEm?.let { AvisoDeGuardado(it, estado.conectado) }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (val carga = estado.metas) {
                    Carga.Carregando -> item { Esqueleto(linhas = 3) }
                    is Carga.Falhou -> item {
                        EstadoDeErro(falhaDe(carga.erro), eventos.tentarDeNovo, mensagem = mensagemDeErro(carga.erro).texto())
                    }
                    is Carga.Pronto -> {
                        item { Orbita(estado) }
                        if (carga.valor.isEmpty()) {
                            item {
                                Vazio(
                                    Icone.Alvo,
                                    stringResource(R.string.metas_vazio_titulo),
                                    texto = stringResource(R.string.metas_vazio_texto),
                                    acao = stringResource(R.string.metas_criar) to eventos.abrirNova,
                                )
                            }
                        } else {
                            items(carga.valor, key = { it.id }) { LinhaDaMeta(it, estado, eventos) }
                            item {
                                Button(eventos.abrirNova, Modifier.fillMaxWidth()) {
                                    Icon(painterResource(Icone.Mais.desenho), null, Modifier.size(18.dp))
                                    Text(stringResource(R.string.metas_criar), Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                        item {
                            Text(
                                stringResource(R.string.metas_rodape),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    if (estado.formulario.aberto) FormularioDaMeta(estado, eventos)
    estado.apagando?.let { meta ->
        AlertDialog(
            onDismissRequest = eventos.desistir,
            confirmButton = {
                TextButton(eventos.confirmar) { Text(stringResource(R.string.metas_apagar), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(eventos.desistir) { Text(stringResource(Comum.string.cancelar)) } },
            text = { Text(stringResource(R.string.metas_apagar_pergunta, meta.titulo)) },
        )
    }
}

/** O cartão de cima: o mês, o anel das concluídas e o resumo. */
@Composable
private fun Orbita(estado: EstadoDasMetas) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.metas_sobretitulo).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(mesPorExtenso(estado.mes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnelDasMetas(estado.percentual)
            Text(stringResource(R.string.metas_para_alcancar), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            Text(
                pluralStringResource(R.plurals.metas_concluidas, estado.lista.size, estado.concluidas, estado.lista.size),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(stringResource(estado.mensagem), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * O anel das metas (anel-das-metas.ts e AnelDasMetas.swift): oito gomos com um corte de 3 graus,
 * que enchem até o [percentual], e o porquinho balançando em cima; parado com menos movimento.
 */
@Composable
internal fun AnelDasMetas(percentual: Int, modifier: Modifier = Modifier) {
    val descricao = stringResource(R.string.metas_anel)
    val cor = MaterialTheme.colorScheme.primary
    val trilho = MaterialTheme.colorScheme.surfaceVariant
    val balanco = if (menosMovimento()) {
        0f
    } else {
        rememberInfiniteTransition(label = "balanço").animateFloat(
            -3f, 3f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "balanço",
        ).value
    }
    Column(
        modifier.clearAndSetSemantics {
            contentDescription = descricao
            progressBarRangeInfo = ProgressBarRangeInfo(percentual / 100f, 0f..1f)
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Porquinho(HumorDoPorquinho.Feliz, Modifier.zIndex(1f).offset(y = 14.dp).rotate(balanco), largura = 70.dp)
        Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                val traco = 22.dp.toPx()
                val area = Size(size.width - traco, size.height - traco)
                val canto = Offset(traco / 2, traco / 2)
                repeat(GOMOS) { indice ->
                    val inicio = -90f + indice * 45f
                    drawArc(trilho, inicio, 42f, false, canto, area, style = Stroke(traco))
                    val cheio = (percentual * 3.6f - indice * 45f).coerceIn(0f, 42f)
                    if (cheio > 0f) drawArc(cor, inicio, cheio, false, canto, area, style = Stroke(traco))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.metas_percentual, percentual), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.metas_concluido), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private const val GOMOS = 8

@Composable
private fun LinhaDaMeta(meta: MetaDoMes, estado: EstadoDasMetas, eventos: EventosDasMinhasMetas) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (meta.concluida) {
                            Icon(
                                painterResource(Icone.Check.desenho),
                                stringResource(R.string.metas_concluida),
                                Modifier.padding(end = 6.dp).size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(meta.titulo, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        stringResource(R.string.metas_de, meta.atual, meta.alvo),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!meta.concluida) {
                    val rotulo = stringResource(R.string.metas_mais_um, meta.titulo)
                    OutlinedButton(
                        { eventos.avancar(meta) },
                        Modifier.semantics { contentDescription = rotulo },
                        enabled = !estado.salvando,
                    ) { Text("+1") }
                }
                IconButton({ eventos.editar(meta) }) {
                    Icon(painterResource(Icone.Lapis.desenho), stringResource(R.string.metas_editar_de, meta.titulo))
                }
                IconButton({ eventos.pedirParaApagar(meta) }) {
                    Icon(painterResource(Icone.Lixeira.desenho), stringResource(R.string.metas_apagar_de, meta.titulo), tint = MaterialTheme.colorScheme.error)
                }
            }
            BarraDeProgresso(estado.progresso(meta), Modifier.padding(end = 12.dp))
        }
    }
}

/** Criar ou editar numa folha (a MetaView do iOS; no site é um cartão). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormularioDaMeta(estado: EstadoDasMetas, eventos: EventosDasMinhasMetas) {
    val f = estado.formulario
    val erro = f.erro?.texto()
    ModalBottomSheet(eventos.fecharFormulario) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(if (f.editando != null) R.string.metas_editar else R.string.metas_criar),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                f.titulo,
                eventos.mudarTitulo,
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.metas_nome)) },
                placeholder = { Text(stringResource(R.string.metas_nome_exemplo)) },
                singleLine = true,
                isError = f.faltou == CampoDaMeta.Titulo,
                supportingText = erro?.takeIf { f.faltou == CampoDaMeta.Titulo }?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            CampoInteiro(f.alvo, eventos.mudarAlvo, stringResource(R.string.metas_alvo), erro.takeIf { f.faltou == CampoDaMeta.Alvo })
            CampoInteiro(f.atual, eventos.mudarAtual, stringResource(R.string.metas_atual), erro.takeIf { f.faltou == CampoDaMeta.Atual })
            if (f.faltou == null && erro != null) {
                Text(erro, Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, color = MaterialTheme.colorScheme.error)
            }
            Button(eventos.salvar, Modifier.fillMaxWidth(), enabled = !estado.salvando) {
                Text(stringResource(if (estado.salvando) R.string.metas_guardando else R.string.metas_salvar))
            }
            TextButton(eventos.fecharFormulario, Modifier.fillMaxWidth()) { Text(stringResource(Comum.string.cancelar)) }
        }
    }
}

@Composable
private fun CampoInteiro(valor: String, aoMudar: (String) -> Unit, rotulo: String, erro: String?) {
    OutlinedTextField(
        valor,
        aoMudar,
        Modifier.fillMaxWidth(),
        label = { Text(rotulo) },
        singleLine = true,
        isError = erro != null,
        supportingText = erro?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
    )
}

@Composable
private fun Faixa(texto: String, fundo: Color, cor: Color) {
    Surface(Modifier.fillMaxWidth(), color = fundo) {
        Text(
            texto,
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp).semantics { liveRegion = LiveRegionMode.Assertive },
            style = MaterialTheme.typography.bodyMedium,
            color = cor,
        )
    }
}

@Composable
private fun MensagemDeErro.texto(): String = texto(LocalContext.current)

/** Por que a lista não carregou, para o ícone e o texto do [EstadoDeErro]. */
internal fun falhaDe(erro: ErroDaApi): FalhaAoCarregar = when {
    erro is ErroDaApi.SemInternet -> FalhaAoCarregar.SemInternet
    erro is ErroDaApi.DoServidor && erro.status >= 500 -> FalhaAoCarregar.DoServidor
    else -> FalhaAoCarregar.Outra
}
