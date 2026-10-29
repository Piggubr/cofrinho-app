package br.com.piggu.feature.painel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.core.data.Carga
import br.com.piggu.core.model.ObjetivoDoCofrinho
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.AvisoDeGuardado
import br.com.piggu.core.ui.ChipsDeVisao
import br.com.piggu.core.ui.EstadoDeErro
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.FalhaAoCarregar
import br.com.piggu.core.ui.SeletorDeMes
import br.com.piggu.core.ui.Visao
import br.com.piggu.core.ui.formatos.dataCurta
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** O que a tela faz; os padrões vazios servem às capturas. */
data class EventosDoPainel(
    val mudarMes: (String) -> Unit = {},
    val trocarVisao: (Visao) -> Unit = {},
    val atualizar: () -> Unit = {},
    val tentarDeNovo: () -> Unit = {},
    val mudarDeposito: (BigDecimal?) -> Unit = {},
    val depositar: () -> Unit = {},
    val excluirDeposito: (String) -> Unit = {},
    val novoObjetivo: () -> Unit = {},
    val editarObjetivo: (ObjetivoDoCofrinho) -> Unit = {},
    val mudarFormulario: (FormularioDeObjetivo) -> Unit = {},
    val fecharFormulario: () -> Unit = {},
    val salvarObjetivo: () -> Unit = {},
    val excluirObjetivo: (String) -> Unit = {},
    val mudarValorDoObjetivo: (String, BigDecimal?) -> Unit = { _, _ -> },
    val depositarNoObjetivo: (String) -> Unit = {},
    val escreverNota: () -> Unit = {},
    val mudarNota: (NotaNova) -> Unit = {},
    val cancelarNota: () -> Unit = {},
    val salvarNota: () -> Unit = {},
    val excluirNota: (String) -> Unit = {},
    val sincronizarBancos: () -> Unit = {},
    val desconectarBanco: (String) -> Unit = {},
    val escolherMoeda: (String) -> Unit = {},
    val usarMoeda: () -> Unit = {},
    val pularGuia: () -> Unit = {},
    val moverBloco: (IdDoBloco, Int) -> Unit = { _, _ -> },
    val moverBlocoPara: (IdDoBloco, Int) -> Unit = { _, _ -> },
    val alternarBloco: (IdDoBloco) -> Unit = {},
    val restaurarPainel: () -> Unit = {},
)

/**
 * O Painel. [abrirTela] recebe o caminho do site (`/orcamentos`, `/gastos`, `/plano`...), e o
 * `app` decide se abre a tela nativa ou o site; [lancarGasto] abre o lançamento rápido.
 */
@Composable
fun TelaDoPainel(
    abrirTela: (caminho: String) -> Unit,
    lancarGasto: () -> Unit,
    modifier: Modifier = Modifier,
    // "Organizar o Painel" do Perfil: a aba abre com a folha de organizar aberta.
    organizarAoAbrir: Boolean = false,
    aoAbrirOrganizar: () -> Unit = {},
    viewModel: PainelViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    TelaDoPainel(
        estado,
        EventosDoPainel(
            mudarMes = viewModel::mudarMes,
            trocarVisao = viewModel::trocarVisao,
            atualizar = viewModel::atualizar,
            tentarDeNovo = viewModel::carregar,
            mudarDeposito = viewModel::mudarDeposito,
            depositar = viewModel::depositar,
            excluirDeposito = viewModel::excluirDeposito,
            novoObjetivo = viewModel::novoObjetivo,
            editarObjetivo = viewModel::editarObjetivo,
            mudarFormulario = viewModel::mudarFormulario,
            fecharFormulario = viewModel::fecharFormulario,
            salvarObjetivo = viewModel::salvarObjetivo,
            excluirObjetivo = viewModel::excluirObjetivo,
            mudarValorDoObjetivo = viewModel::mudarValorDoObjetivo,
            depositarNoObjetivo = viewModel::depositarNoObjetivo,
            escreverNota = viewModel::escreverNota,
            mudarNota = viewModel::mudarNota,
            cancelarNota = viewModel::cancelarNota,
            salvarNota = viewModel::salvarNota,
            excluirNota = viewModel::excluirNota,
            sincronizarBancos = viewModel::sincronizarBancos,
            desconectarBanco = viewModel::desconectarBanco,
            escolherMoeda = viewModel::escolherMoeda,
            usarMoeda = viewModel::usarMoeda,
            pularGuia = viewModel::pularGuia,
            moverBloco = viewModel::moverBloco,
            moverBlocoPara = viewModel::moverBlocoPara,
            alternarBloco = viewModel::alternarBloco,
            restaurarPainel = viewModel::restaurarPainel,
        ),
        abrirTela,
        lancarGasto,
        modifier,
        organizarAoAbrir = organizarAoAbrir,
        aoAbrirOrganizar = aoAbrirOrganizar,
    )
}

/** Uma confirmação antes de apagar (o confirm() do site). */
internal data class Pergunta(val texto: String, val botao: String, val sim: () -> Unit)

/** O Painel sem o ViewModel: desenha o [estado] e chama os [eventos]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDoPainel(
    estado: EstadoDoPainel,
    eventos: EventosDoPainel,
    abrirTela: (caminho: String) -> Unit,
    lancarGasto: () -> Unit,
    modifier: Modifier = Modifier,
    organizarAoAbrir: Boolean = false,
    aoAbrirOrganizar: () -> Unit = {},
) {
    var organizando by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(organizarAoAbrir) {
        if (organizarAoAbrir) {
            organizando = true
            aoAbrirOrganizar()
        }
    }
    var pergunta by remember { mutableStateOf<Pergunta?>(null) }
    val perguntar: (Pergunta) -> Unit = { pergunta = it }
    val largura = Modifier.widthIn(max = 720.dp).fillMaxWidth()

    PullToRefreshBox(estado.atualizando, eventos.atualizar, modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "topo") { Topo(estado, eventos, largura) }
            when (val carga = estado.carga) {
                Carga.Carregando -> item(key = "carregando") { Esqueleto(largura, linhas = 4) }
                is Carga.Falhou -> item(key = "erro") {
                    val erro = carga.erro
                    EstadoDeErro(
                        when {
                            erro is ErroDaApi.SemInternet -> FalhaAoCarregar.SemInternet
                            erro is ErroDaApi.DoServidor && erro.status >= 500 -> FalhaAoCarregar.DoServidor
                            else -> FalhaAoCarregar.Outra
                        },
                        eventos.tentarDeNovo,
                        largura,
                        mensagemDeErro(erro).texto(LocalContext.current),
                    )
                }
                is Carga.Pronto -> {
                    carga.guardadaEm?.let { quando ->
                        item(key = "guardado") { AvisoDeGuardado(quandoFoiGuardado(quando), estado.conectado, largura) }
                    }
                    if (estado.mostraGuia) item(key = "guia") { CartaoDoGuia(estado, eventos, abrirTela, lancarGasto, largura) }
                    items(estado.cartoes, key = { it.id }) { bloco ->
                        Column(largura, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Cartao(bloco, estado, eventos, abrirTela, lancarGasto, perguntar)
                            if (bloco == estado.bancosJuntoDe) CartaoDosBancos(estado, eventos, abrirTela, perguntar)
                        }
                    }
                    item(key = "personalizar") {
                        TextButton({ organizando = true }) { Text(stringResource(R.string.personalizar)) }
                    }
                }
            }
        }
    }

    if (organizando) OrganizarPainel(estado.blocos, eventos) { organizando = false }

    pergunta?.let { atual ->
        AlertDialog(
            onDismissRequest = { pergunta = null },
            text = { Text(atual.texto) },
            confirmButton = {
                TextButton({
                    pergunta = null
                    atual.sim()
                }) { Text(atual.botao, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ pergunta = null }) { Text(stringResource(R.string.cancelar)) } },
        )
    }
}

@Composable
private fun Topo(estado: EstadoDoPainel, eventos: EventosDoPainel, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (estado.primeiroNome.isEmpty()) stringResource(R.string.painel_titulo) else stringResource(R.string.ola, estado.primeiroNome),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
        )
        SeletorDeMes(estado.mes, estado.mesAtual, eventos.mudarMes)
        // Tudo, Meu ou Compartilhado: só para quem mora com alguém, e o membro não vê os gastos.
        ChipsDeVisao(estado.visao, eventos.trocarVisao, estado.moraComAlguem && !estado.ehMembro)
    }
}

@Composable
private fun Cartao(
    bloco: IdDoBloco,
    estado: EstadoDoPainel,
    eventos: EventosDoPainel,
    abrirTela: (String) -> Unit,
    lancarGasto: () -> Unit,
    perguntar: (Pergunta) -> Unit,
) {
    when (bloco) {
    IdDoBloco.Resumo -> estado.resumo?.let { CartaoDoResumo(it, estado.moeda, abrirTela) }
    IdDoBloco.Avisos -> CartaoDeAvisos(estado.avisos, estado.moeda, abrirTela)
    IdDoBloco.Mes -> CartaoDoMes(estado, abrirTela)
    IdDoBloco.Cofrinho -> CartaoDoCofrinho(estado, eventos, perguntar)
    IdDoBloco.Objetivos -> CartaoDosObjetivos(estado, eventos, abrirTela, perguntar)
    IdDoBloco.Metas -> CartaoDasMetas(estado, abrirTela)
    IdDoBloco.Categorias -> CartaoDasCategorias(estado, lancarGasto)
    IdDoBloco.Notas -> CartaoDasNotas(estado, eventos, abrirTela, perguntar)
    IdDoBloco.GirlsMath -> CartaoDoGirlsMath(estado, abrirTela)
    }
}

/** "28/10/2026 às 10:00", na hora do aparelho. */
@Composable
private fun quandoFoiGuardado(millis: Long): String {
    val quando = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return stringResource(R.string.guardado_quando, dataCurta(quando.toLocalDate().toString()), String.format(Locale.ROOT, "%02d:%02d", quando.hour, quando.minute))
}
