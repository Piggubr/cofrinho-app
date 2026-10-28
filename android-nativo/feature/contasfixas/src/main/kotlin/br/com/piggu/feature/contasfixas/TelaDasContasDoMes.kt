package br.com.piggu.feature.contasfixas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.model.ContaFixa
import br.com.piggu.core.model.SituacaoDaContaFixa
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.AvisoDeGuardado
import br.com.piggu.core.ui.CampoDeCategoria
import br.com.piggu.core.ui.CampoDeValor
import br.com.piggu.core.ui.EstadoDeErro
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.FalhaAoCarregar
import br.com.piggu.core.ui.SeletorDeMes
import br.com.piggu.core.ui.Vazio
import br.com.piggu.core.ui.formatos.dataCurta
import br.com.piggu.core.ui.formatos.formatarMoeda
import java.math.BigDecimal
import br.com.piggu.core.ui.R as Comum

data class EventosDasContasDoMes(
    val voltar: () -> Unit = {},
    val mudarMes: (String) -> Unit = {},
    val tentarDeNovo: () -> Unit = {},
    val pagar: (ContaFixa) -> Unit = {},
    val pedirParaApagar: (ContaFixa) -> Unit = {},
    val pedirParaPararDeRepetir: (ContaFixa) -> Unit = {},
    val confirmar: () -> Unit = {},
    val desistir: () -> Unit = {},
    val abrirFormulario: () -> Unit = {},
    val fecharFormulario: () -> Unit = {},
    val mudarDescricao: (String) -> Unit = {},
    val mudarCategoria: (String) -> Unit = {},
    val mudarValor: (BigDecimal?) -> Unit = {},
    val mudarDia: (String) -> Unit = {},
    val mudarAutomatico: (Boolean) -> Unit = {},
    val criar: () -> Unit = {},
    /** Cria a categoria e devolve a lista nova; a falha traz o texto para a tela. */
    val criarCategoria: suspend (String) -> Result<List<String>> = { Result.failure(UnsupportedOperationException()) },
    val categoriasCriadas: (List<String>) -> Unit = {},
)

/** Contas do mês ligada ao [ContasDoMesViewModel]: tela cheia, aberta pelo Mais. */
@Composable
fun TelaDasContasDoMes(voltar: () -> Unit, modifier: Modifier = Modifier, viewModel: ContasDoMesViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    TelaDasContasDoMes(
        estado,
        EventosDasContasDoMes(
            voltar = voltar,
            mudarMes = viewModel::mudarMes,
            tentarDeNovo = viewModel::tentarDeNovo,
            pagar = viewModel::pagar,
            pedirParaApagar = viewModel::pedirParaApagar,
            pedirParaPararDeRepetir = viewModel::pedirParaPararDeRepetir,
            confirmar = viewModel::confirmar,
            desistir = viewModel::desistir,
            abrirFormulario = viewModel::abrirFormulario,
            fecharFormulario = viewModel::fecharFormulario,
            mudarDescricao = viewModel::mudarDescricao,
            mudarCategoria = viewModel::mudarCategoria,
            mudarValor = viewModel::mudarValor,
            mudarDia = viewModel::mudarDia,
            mudarAutomatico = viewModel::mudarAutomatico,
            criar = viewModel::criar,
            criarCategoria = { nome ->
                viewModel.criarCategoria(nome).recoverCatching { falha -> throw IllegalStateException(mensagemDeErro(falha).texto(contexto)) }
            },
            categoriasCriadas = viewModel::categoriasCriadas,
        ),
        modifier,
    )
}

/** A tela só desenhada (capturas e prévias). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDasContasDoMes(estado: EstadoDasContasDoMes, eventos: EventosDasContasDoMes, modifier: Modifier = Modifier) {
    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contas_titulo)) },
                navigationIcon = {
                    IconButton(eventos.voltar) { Icon(painterResource(Icone.Esquerda.desenho), stringResource(Comum.string.voltar)) }
                },
                actions = {
                    IconButton(eventos.abrirFormulario) { Icon(painterResource(Icone.Mais.desenho), stringResource(R.string.contas_nova)) }
                },
            )
        },
    ) { margens ->
        Column(Modifier.padding(margens).fillMaxSize().wrapContentWidth().widthIn(max = 720.dp)) {
            SeletorDeMes(estado.mes, estado.mesAtual, eventos.mudarMes, Modifier.align(Alignment.CenterHorizontally))
            estado.erro?.let { Faixa(it.texto(), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, LiveRegionMode.Assertive) }
            estado.aviso?.let { Faixa(stringResource(it), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, LiveRegionMode.Polite) }
            estado.guardadoEm?.let { AvisoDeGuardado(it, estado.conectado) }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (val carga = estado.contas) {
                    Carga.Carregando -> item { Esqueleto(linhas = 4) }
                    is Carga.Falhou -> item {
                        EstadoDeErro(falhaDe(carga.erro), eventos.tentarDeNovo, mensagem = mensagemDeErro(carga.erro).texto())
                    }
                    is Carga.Pronto -> if (carga.valor.isEmpty()) {
                        item {
                            Vazio(
                                Icone.Recibo,
                                stringResource(R.string.contas_vazio_titulo),
                                texto = stringResource(R.string.contas_vazio_texto),
                                acao = stringResource(R.string.contas_vazio_acao) to eventos.abrirFormulario,
                            )
                        }
                    } else {
                        item {
                            Text(
                                stringResource(R.string.contas_em_aberto, formatarMoeda(estado.totalEmAberto, estado.moeda)),
                                Modifier.semantics { heading() },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        grupo(R.string.contas_vencidas, estado.vencidas, estado, eventos)
                        grupo(R.string.contas_a_vencer, estado.aVencer, estado, eventos)
                        grupo(R.string.contas_pagas, estado.pagas, estado, eventos)
                    }
                }
                if (estado.receitasFixas.isNotEmpty()) item { ReceitasFixas(estado, eventos) }
            }
        }
    }
    if (estado.formulario.aberto) NovaContaFixa(estado, eventos)
    estado.confirmacao?.let { Confirmar(it, eventos) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.grupo(
    titulo: Int,
    contas: List<ContaFixa>,
    estado: EstadoDasContasDoMes,
    eventos: EventosDasContasDoMes,
) {
    if (contas.isEmpty()) return
    item(key = titulo) {
        Cartao(stringResource(titulo), if (titulo == R.string.contas_vencidas) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) {
            contas.forEach { LinhaDaConta(it, estado, eventos) }
        }
    }
}

@Composable
private fun LinhaDaConta(conta: ContaFixa, estado: EstadoDasContasDoMes, eventos: EventosDasContasDoMes) {
    val detalhe = listOfNotNull(
        dataCurta(conta.vencimento),
        conta.categoria,
        stringResource(R.string.contas_lanca_sozinha).takeIf { conta.automatico },
    ).joinToString(" · ")
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(conta.descricao, style = MaterialTheme.typography.bodyLarge)
                Text(detalhe, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatarMoeda(conta.valor, estado.moeda), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            Lixeira(stringResource(R.string.contas_apagar_de, conta.descricao), !estado.ocupado) { eventos.pedirParaApagar(conta) }
        }
        if (conta.situacao != SituacaoDaContaFixa.PAGA) {
            val rotulo = stringResource(R.string.contas_marcar_como_paga_de, conta.descricao)
            OutlinedButton(
                { eventos.pagar(conta) },
                Modifier.heightIn(min = 48.dp).semantics { contentDescription = rotulo },
                enabled = !estado.ocupado,
            ) { Text(stringResource(R.string.contas_marcar_como_paga)) }
        }
    }
}

@Composable
private fun ReceitasFixas(estado: EstadoDasContasDoMes, eventos: EventosDasContasDoMes) {
    Cartao(stringResource(R.string.contas_receitas_titulo)) {
        estado.receitasFixas.forEach { fixa ->
            val segundoDia = fixa.segundoDia
            val segundoValor = fixa.segundoValor
            val detalhe = if (segundoDia != null && segundoValor != null) {
                stringResource(
                    R.string.contas_receitas_duas_parcelas,
                    fixa.dia, formatarMoeda(fixa.valor, estado.moeda), segundoDia, formatarMoeda(segundoValor, estado.moeda), fixa.categoria,
                )
            } else {
                stringResource(R.string.contas_receitas_todo_dia, fixa.dia, fixa.categoria)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Text(fixa.descricao, style = MaterialTheme.typography.bodyLarge)
                    Text(detalhe, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatarMoeda(fixa.valor + (segundoValor ?: BigDecimal.ZERO), estado.moeda),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Lixeira(stringResource(R.string.contas_parar_de_repetir_de, fixa.descricao), !estado.ocupado) { eventos.pedirParaPararDeRepetir(fixa) }
            }
        }
        Text(stringResource(R.string.contas_receitas_explicacao), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Nova conta fixa numa folha (a NovaContaFixaView do iOS; no site é um cartão abaixo da lista). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovaContaFixa(estado: EstadoDasContasDoMes, eventos: EventosDasContasDoMes) {
    val f = estado.formulario
    ModalBottomSheet(eventos.fecharFormulario) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.contas_nova), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            f.erro?.let {
                Text(it.texto(), Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                f.descricao,
                eventos.mudarDescricao,
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.contas_descricao)) },
                placeholder = { Text(stringResource(R.string.contas_descricao_exemplo)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            CampoDeCategoria(
                estado.categorias,
                f.categoria,
                eventos.mudarCategoria,
                eventos.criarCategoria,
                eventos.categoriasCriadas,
                Modifier.fillMaxWidth(),
                automatica = false,
            )
            CampoDeValor(f.valor, eventos.mudarValor, estado.moeda, Modifier.fillMaxWidth())
            OutlinedTextField(
                f.dia,
                eventos.mudarDia,
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.contas_dia)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(f.automatico, role = Role.Switch, onValueChange = eventos.mudarAutomatico),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.contas_automatico), Modifier.weight(1f).padding(end = 12.dp))
                Switch(f.automatico, onCheckedChange = null)
            }
            Button(eventos.criar, Modifier.fillMaxWidth(), enabled = !estado.ocupado) { Text(stringResource(R.string.contas_criar)) }
        }
    }
}

@Composable
private fun Confirmar(confirmacao: Confirmacao, eventos: EventosDasContasDoMes) {
    val (pergunta, botao) = when (confirmacao) {
        is Confirmacao.ApagarConta -> stringResource(R.string.contas_apagar_pergunta, confirmacao.conta.descricao) to R.string.contas_apagar
        is Confirmacao.PararDeRepetir -> stringResource(R.string.contas_parar_pergunta, confirmacao.fixa.descricao) to R.string.contas_parar_de_repetir
    }
    AlertDialog(
        onDismissRequest = eventos.desistir,
        confirmButton = { TextButton(eventos.confirmar) { Text(stringResource(botao), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(eventos.desistir) { Text(stringResource(Comum.string.cancelar)) } },
        text = { Text(pergunta) },
    )
}

@Composable
private fun Faixa(texto: String, fundo: Color, cor: Color, anuncio: LiveRegionMode) {
    Surface(Modifier.fillMaxWidth(), color = fundo) {
        Text(
            texto,
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp).semantics { liveRegion = anuncio },
            style = MaterialTheme.typography.bodyMedium,
            color = cor,
        )
    }
}

@Composable
private fun Cartao(titulo: String, corDoTitulo: Color = MaterialTheme.colorScheme.onSurface, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titulo, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = corDoTitulo)
            conteudo()
        }
    }
}

@Composable
private fun Lixeira(descricao: String, ativa: Boolean, aoTocar: () -> Unit) {
    IconButton(aoTocar, enabled = ativa) {
        Icon(painterResource(Icone.Lixeira.desenho), descricao, tint = MaterialTheme.colorScheme.error)
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
