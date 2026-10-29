package br.com.piggu.feature.gastos.lista

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.AvisoDeGuardado
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.EstadoDeErro
import br.com.piggu.core.ui.FalhaAoCarregar
import br.com.piggu.core.ui.Vazio
import br.com.piggu.feature.gastos.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Tipos que o seletor oferece: o CSV e o OFX (que muitos aparelhos chamam de octet-stream). */
private val TIPOS_DO_EXTRATO = arrayOf("text/*", "application/x-ofx", "application/ofx", "application/vnd.intu.qfx", "application/octet-stream")

/** A folga do + da moldura, para o último item não ficar embaixo dele. */
private val FOLGA_DO_LANCAR = 88.dp

/**
 * A aba Gastos.
 *
 * @param gastoParaAbrir id e mês (aaaa-mm, nulo é o atual) do link `piggu://gastos?id=...&mes=...`.
 * @param aoAbrirGasto o pedido foi entregue à tela: quem chamou pode esquecê-lo.
 * @param lancarGasto o formulário de lançar (o + da moldura).
 */
@Composable
fun TelaDeGastos(
    gastoParaAbrir: Pair<String, String?>?,
    aoAbrirGasto: () -> Unit,
    lancarGasto: () -> Unit,
    viewModel: GastosViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val avisos = remember { SnackbarHostState() }

    LaunchedEffect(gastoParaAbrir) {
        gastoParaAbrir?.let { (id, mes) ->
            viewModel.abrir(id, mes)
            aoAbrirGasto()
        }
    }
    val escolher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.lerExtrato { contexto.contentResolver.openInputStream(it) } }
    }
    LaunchedEffect(estado.csv) {
        val csv = estado.csv ?: return@LaunchedEffect
        compartilhar(contexto, csv)
        viewModel.csvEntregue()
    }

    ConteudoDosGastos(
        estado = estado,
        acoes = viewModel,
        avisos = avisos,
        lancarGasto = lancarGasto,
        escolherExtrato = { escolher.launch(TIPOS_DO_EXTRATO) },
        // O campo de categoria mostra a mensagem da falha: a do servidor, já em português.
        criarCategoria = { nome ->
            viewModel.criarCategoria(nome).fold(
                { Result.success(it) },
                { Result.failure(Exception(mensagemDeErro(it).texto(contexto))) },
            )
        },
    )
}

/** Grava o CSV na pasta de cache e abre o compartilhamento do Android, pelo FileProvider. */
private suspend fun compartilhar(contexto: Context, csv: CsvDoMes) {
    val arquivo = withContext(Dispatchers.IO) {
        File(contexto.cacheDir, "exportados").apply { mkdirs() }.resolve(csv.nome).apply { writeBytes(csv.bytes) }
    }
    val endereco = FileProvider.getUriForFile(contexto, "${contexto.packageName}.gastos.arquivos", arquivo)
    val envio = Intent(Intent.ACTION_SEND)
        .setType("text/csv")
        .putExtra(Intent.EXTRA_STREAM, endereco)
        .putExtra(Intent.EXTRA_TITLE, csv.nome)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    contexto.startActivity(Intent.createChooser(envio, contexto.getString(R.string.importar_compartilhar)))
}

/**
 * A tela inteira, sem ViewModel: desenha o [estado] e chama as [acoes] (as capturas passam
 * `object : AcoesDosGastos {}`).
 */
@Composable
fun ConteudoDosGastos(
    estado: EstadoDosGastos,
    acoes: AcoesDosGastos,
    avisos: SnackbarHostState,
    lancarGasto: () -> Unit,
    escolherExtrato: () -> Unit,
    criarCategoria: suspend (String) -> Result<List<String>>,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    val apagado = estado.exclusao?.let { stringResource(R.string.gastos_apagado, it.gasto.item) }
    val desfazer = stringResource(R.string.gastos_desfazer)
    LaunchedEffect(estado.exclusao) {
        apagado ?: return@LaunchedEffect
        when (avisos.showSnackbar(apagado, desfazer, withDismissAction = false, duration = SnackbarDuration.Long)) {
            SnackbarResult.ActionPerformed -> acoes.desfazer()
            SnackbarResult.Dismissed -> acoes.confirmarExclusao()
        }
    }
    val texto = estado.aviso?.let { stringResource(it.texto, *it.argumentos.toTypedArray()) } ?: estado.erro?.texto(contexto)
    LaunchedEffect(texto) {
        texto ?: return@LaunchedEffect
        avisos.showSnackbar(texto)
        acoes.avisoMostrado()
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = FOLGA_DO_LANCAR)) {
            item(key = "topo") { Topo(estado, acoes) }
            if (estado.recusados > 0) item(key = "recusados") { AvisoDeRecusados(estado.recusados, acoes::esquecerRecusados) }
            estado.sugestao?.let { sugestao -> item(key = "sugestao") { SugestaoDeRegra(sugestao, acoes) } }

            when (val carga = estado.carga) {
                Carga.Carregando -> item(key = "carregando") { Esqueleto() }
                is Carga.Falhou -> item(key = "erro") {
                    val falha = when {
                        carga.erro is ErroDaApi.SemInternet -> FalhaAoCarregar.SemInternet
                        (carga.erro as? ErroDaApi.DoServidor)?.status?.let { it >= 500 } == true -> FalhaAoCarregar.DoServidor
                        else -> FalhaAoCarregar.Outra
                    }
                    EstadoDeErro(falha, acoes::recarregar, mensagem = mensagemDeErro(carga.erro).texto(contexto))
                }
                is Carga.Pronto -> {
                    carga.guardadaEm?.let { quando ->
                        item(key = "guardado") { AvisoDeGuardado(horaGuardada(quando), estado.conectado) }
                    }
                    lista(estado, acoes, lancarGasto, escolherExtrato)
                }
            }

            if (estado.acerto.isNotEmpty()) item(key = "divisao") { DivisaoDoMes(estado) }
            item(key = "importar") { ImportarEExportar(estado, acoes, escolherExtrato) }
            item(key = "regras") { RegrasDeCategoria(estado, acoes, criarCategoria) }
        }
        SnackbarHost(avisos, Modifier.align(Alignment.BottomCenter).padding(bottom = FOLGA_DO_LANCAR))
    }

    estado.edicao?.let { EditarGasto(it, estado.categorias, estado.moeda, acoes, criarCategoria) }
    estado.confirmando?.let { ConfirmarExclusao(it, acoes) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.lista(
    estado: EstadoDosGastos,
    acoes: AcoesDosGastos,
    lancarGasto: () -> Unit,
    escolherExtrato: () -> Unit,
) {
    if (estado.vazio) {
        item(key = "vazio") {
            Vazio(
                Icone.Recibo,
                stringResource(R.string.gastos_vazio_titulo),
                texto = stringResource(R.string.gastos_vazio_texto),
                acao = stringResource(R.string.gastos_lancar_um) to lancarGasto,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(escolherExtrato) { Text(stringResource(R.string.gastos_importar_extrato)) }
            }
        }
        return
    }
    item(key = "total") { Total(estado, acoes) }
    val dias = estado.dias
    if (dias.isEmpty()) {
        item(key = "nada") {
            Text(
                stringResource(R.string.gastos_nada_nos_filtros),
                Modifier.fillMaxWidth().padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    dias.forEach { dia ->
        item(key = "dia-${dia.dia}") {
            Text(
                diaPorExtenso(dia.dia),
                Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        items(dia.gastos, key = { it.gasto.id }) { linha -> LinhaDoGasto(linha, estado, acoes) }
    }
}

/** O FileProvider do CSV exportado (AndroidManifest.xml deste módulo). */
class ArquivosDosGastos : FileProvider()

private val HORA_GUARDADA =DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm")

private fun horaGuardada(millis: Long): String =
    HORA_GUARDADA.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
