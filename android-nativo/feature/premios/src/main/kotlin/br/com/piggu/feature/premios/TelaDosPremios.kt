package br.com.piggu.feature.premios

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
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
import br.com.piggu.core.model.MovimentoDeMoedas
import br.com.piggu.core.model.Premio
import br.com.piggu.core.model.Resgate
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.AvisoDeGuardado
import br.com.piggu.core.ui.EstadoDeErro
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.FalhaAoCarregar
import br.com.piggu.core.ui.Vazio
import br.com.piggu.core.ui.formatos.dataCurta
import br.com.piggu.core.ui.R as Comum

data class EventosDosPremios(
    val voltar: () -> Unit = {},
    val tentarDeNovo: () -> Unit = {},
    val alternarHistorico: () -> Unit = {},
    val mudarAjusteValor: (String) -> Unit = {},
    val mudarAjusteMotivo: (String) -> Unit = {},
    val ajustar: () -> Unit = {},
    val pedirParaResgatar: (Premio) -> Unit = {},
    val pedirParaRemover: (Premio) -> Unit = {},
    val confirmar: () -> Unit = {},
    val desistir: () -> Unit = {},
    val abrirNovoPremio: () -> Unit = {},
    val editarPremio: (Premio) -> Unit = {},
    val fecharFormulario: () -> Unit = {},
    val mudarNome: (String) -> Unit = {},
    val mudarDescricao: (String) -> Unit = {},
    val mudarPreco: (String) -> Unit = {},
    val salvarPremio: () -> Unit = {},
)

/** Prêmios ligada ao [PremiosViewModel]: tela cheia, aberta pelo Mais. */
@Composable
fun TelaDosPremios(voltar: () -> Unit, modifier: Modifier = Modifier, viewModel: PremiosViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    TelaDosPremios(
        estado,
        EventosDosPremios(
            voltar = voltar,
            tentarDeNovo = viewModel::tentarDeNovo,
            alternarHistorico = viewModel::alternarHistorico,
            mudarAjusteValor = viewModel::mudarAjusteValor,
            mudarAjusteMotivo = viewModel::mudarAjusteMotivo,
            ajustar = viewModel::ajustar,
            pedirParaResgatar = viewModel::pedirParaResgatar,
            pedirParaRemover = viewModel::pedirParaRemover,
            confirmar = viewModel::confirmar,
            desistir = viewModel::desistir,
            abrirNovoPremio = viewModel::abrirNovoPremio,
            editarPremio = viewModel::editarPremio,
            fecharFormulario = viewModel::fecharFormulario,
            mudarNome = viewModel::mudarNome,
            mudarDescricao = viewModel::mudarDescricao,
            mudarPreco = viewModel::mudarPreco,
            salvarPremio = viewModel::salvarPremio,
        ),
        modifier,
    )
}

/** A tela só desenhada (capturas e prévias). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDosPremios(estado: EstadoDosPremios, eventos: EventosDosPremios, modifier: Modifier = Modifier) {
    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.premios_titulo)) },
                navigationIcon = {
                    IconButton(eventos.voltar) { Icon(painterResource(Icone.Esquerda.desenho), stringResource(Comum.string.voltar)) }
                },
                actions = {
                    if (estado.cuidaDosPremios) {
                        IconButton(eventos.abrirNovoPremio) { Icon(painterResource(Icone.Mais.desenho), stringResource(R.string.premios_novo)) }
                    }
                },
            )
        },
    ) { margens ->
        Column(Modifier.padding(margens).fillMaxSize().wrapContentWidth().widthIn(max = 720.dp)) {
            estado.erro?.let { Faixa(it.texto(), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, LiveRegionMode.Assertive) }
            estado.aviso?.let { Faixa(it.texto(), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, LiveRegionMode.Polite) }
            estado.guardadoEm?.let { AvisoDeGuardado(it, estado.conectado) }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (val carga = estado.carga) {
                    Carga.Carregando -> item { Esqueleto(linhas = 4) }
                    is Carga.Falhou -> item {
                        EstadoDeErro(falhaDe(carga.erro), eventos.tentarDeNovo, mensagem = mensagemDeErro(carga.erro).texto())
                    }
                    is Carga.Pronto -> {
                        item { Fofocoins(estado, carga.valor.saldo.historico, eventos) }
                        item { Disponiveis(estado, carga.valor.premios, eventos) }
                        item { Resgates(estado, carga.valor.resgates) }
                    }
                }
            }
        }
    }
    if (estado.formulario.aberto) FormularioDoPremio(estado, eventos)
    estado.confirmacao?.let { Confirmar(it, eventos) }
}

@Composable
private fun Fofocoins(estado: EstadoDosPremios, historico: List<MovimentoDeMoedas>, eventos: EventosDosPremios) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.premios_fofocoins), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(
                    estado.saldo.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (estado.cuidaDosPremios) {
                OutlinedTextField(
                    estado.ajusteValor,
                    eventos.mudarAjusteValor,
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.premios_quantidade)) },
                    placeholder = { Text(stringResource(R.string.premios_quantidade_exemplo)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    estado.ajusteMotivo,
                    eventos.mudarAjusteMotivo,
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.premios_motivo)) },
                    placeholder = { Text(stringResource(R.string.premios_motivo_exemplo)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                Button(eventos.ajustar, Modifier.fillMaxWidth(), enabled = !estado.ocupado) { Text(stringResource(R.string.premios_ajustar)) }
            }
            TextButton(eventos.alternarHistorico, Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (estado.mostrarHistorico) R.string.premios_esconder_historico else R.string.premios_ver_historico))
            }
            if (estado.mostrarHistorico) {
                if (historico.isEmpty()) Suave(stringResource(R.string.premios_sem_movimentacao))
                historico.forEach { movimento ->
                    Linha(movimento.motivo, dataCurta(movimento.data)) {
                        Text(
                            if (movimento.valor > 0) "+${movimento.valor}" else movimento.valor.toString(),
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                movimento.valor > 0 -> MaterialTheme.colorScheme.primary
                                movimento.valor < 0 -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Disponiveis(estado: EstadoDosPremios, premios: List<Premio>, eventos: EventosDosPremios) {
    Cartao(stringResource(R.string.premios_disponiveis)) {
        if (premios.isEmpty()) {
            Vazio(
                Icone.Trofeu,
                stringResource(R.string.premios_vazio_titulo),
                texto = stringResource(R.string.premios_vazio_texto),
                compacto = true,
                acao = if (estado.cuidaDosPremios) stringResource(R.string.premios_vazio_acao) to eventos.abrirNovoPremio else null,
            )
        }
        premios.forEach { premio -> LinhaDoPremio(premio, estado, eventos) }
    }
}

@Composable
private fun LinhaDoPremio(premio: Premio, estado: EstadoDosPremios, eventos: EventosDosPremios) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(
                    premio.nome,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (premio.ativo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                premio.descricao?.takeIf { it.isNotEmpty() }?.let { Suave(it) }
                Text(
                    stringResource(R.string.premios_preco, premio.preco),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (!premio.ativo) Suave(stringResource(R.string.premios_desativado))
            }
            if (estado.cuidaDosPremios) {
                IconButton({ eventos.editarPremio(premio) }, enabled = !estado.ocupado) {
                    Icon(painterResource(Icone.Lapis.desenho), stringResource(R.string.premios_editar_de, premio.nome))
                }
                if (premio.ativo) {
                    IconButton({ eventos.pedirParaRemover(premio) }, enabled = !estado.ocupado) {
                        Icon(painterResource(Icone.Lixeira.desenho), stringResource(R.string.premios_remover_de, premio.nome), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        if (premio.ativo) {
            val rotulo = stringResource(R.string.premios_resgatar_de, premio.nome)
            Button(
                { eventos.pedirParaResgatar(premio) },
                Modifier.heightIn(min = 48.dp).semantics { contentDescription = rotulo },
                enabled = estado.podeResgatar(premio),
            ) { Text(stringResource(R.string.premios_resgatar)) }
        }
    }
}

@Composable
private fun Resgates(estado: EstadoDosPremios, resgates: List<Resgate>) {
    Cartao(stringResource(R.string.premios_resgates)) {
        if (resgates.isEmpty()) Suave(stringResource(R.string.premios_sem_resgates))
        resgates.forEach { resgate ->
            Linha(resgate.premio, stringResource(R.string.premios_quem_e_status, estado.quemResgatou(resgate).texto(), resgate.status)) {
                Text(resgate.preco.toString(), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Novo prêmio ou editar um, numa folha (no site é um cartão acima da lista). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormularioDoPremio(estado: EstadoDosPremios, eventos: EventosDosPremios) {
    val f = estado.formulario
    ModalBottomSheet(eventos.fecharFormulario) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (f.editando == null) R.string.premios_novo else R.string.premios_editar),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            f.erro?.let {
                Text(it.texto(), Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                f.nome,
                eventos.mudarNome,
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.premios_nome)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                f.descricao,
                eventos.mudarDescricao,
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.premios_descricao)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                f.preco,
                eventos.mudarPreco,
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.premios_preco_rotulo)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
            Button(eventos.salvarPremio, Modifier.fillMaxWidth(), enabled = !estado.ocupado) {
                Text(stringResource(if (f.editando == null) R.string.premios_criar else R.string.premios_salvar))
            }
        }
    }
}

@Composable
private fun Confirmar(confirmacao: Confirmacao, eventos: EventosDosPremios) {
    val (pergunta, botao, perigo) = when (confirmacao) {
        is Confirmacao.Resgatar -> Triple(
            stringResource(R.string.premios_resgatar_pergunta, confirmacao.premio.nome, confirmacao.premio.preco),
            R.string.premios_resgatar,
            false,
        )
        is Confirmacao.Remover -> Triple(stringResource(R.string.premios_remover_pergunta, confirmacao.premio.nome), R.string.premios_remover, true)
    }
    AlertDialog(
        onDismissRequest = eventos.desistir,
        confirmButton = {
            TextButton(eventos.confirmar) {
                Text(stringResource(botao), color = if (perigo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(eventos.desistir) { Text(stringResource(Comum.string.cancelar)) } },
        text = { Text(pergunta) },
    )
}

@Composable
private fun Linha(titulo: String, detalhe: String, valor: @Composable () -> Unit) {
    Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Suave(detalhe)
        }
        valor()
    }
}

@Composable
private fun Suave(texto: String) =
    Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

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
private fun Cartao(titulo: String, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titulo, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            conteudo()
        }
    }
}

@Composable
private fun Aviso.texto(): String = stringResource(texto, *argumentos.toTypedArray())

@Composable
private fun MensagemDeErro.texto(): String = texto(LocalContext.current)

/** Por que a tela não carregou, para o ícone e o texto do [EstadoDeErro]. */
internal fun falhaDe(erro: ErroDaApi): FalhaAoCarregar = when {
    erro is ErroDaApi.SemInternet -> FalhaAoCarregar.SemInternet
    erro is ErroDaApi.DoServidor && erro.status >= 500 -> FalhaAoCarregar.DoServidor
    else -> FalhaAoCarregar.Outra
}
