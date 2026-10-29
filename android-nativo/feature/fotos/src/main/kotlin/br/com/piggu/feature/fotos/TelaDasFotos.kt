package br.com.piggu.feature.fotos

import android.content.pm.PackageManager
import androidx.compose.ui.semantics.heading
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.model.FotoDoFeed
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.AvisoDeGuardado
import br.com.piggu.core.ui.EstadoDeErro
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.FalhaAoCarregar
import br.com.piggu.core.ui.ImagemDoAsset
import br.com.piggu.core.ui.SeletorDeMes
import br.com.piggu.core.ui.Vazio
import java.io.File
import br.com.piggu.core.ui.R as Comum

data class EventosDasFotos(
    val voltar: () -> Unit = {},
    val abrirPremium: () -> Unit = {},
    val tentarDeNovo: () -> Unit = {},
    val mudarMes: (String) -> Unit = {},
    /** Abre o Photo Picker. */
    val escolherDaGaleria: () -> Unit = {},
    /** Abre a câmera; nulo esconde o botão (aparelho sem câmera). */
    val tirarFoto: (() -> Unit)? = null,
    val mudarLegenda: (FotoDoFeed, String) -> Unit = { _, _ -> },
    val pedirParaApagar: (FotoDoFeed) -> Unit = {},
    val confirmar: () -> Unit = {},
    val desistir: () -> Unit = {},
)

/** Fotos do mês ligada ao [FotosViewModel]: tela cheia. [abrirPremium] leva ao plano quem quer enviar sem Premium. */
@Composable
fun TelaDasFotos(voltar: () -> Unit, abrirPremium: () -> Unit, modifier: Modifier = Modifier, viewModel: FotosViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.enviar { contexto.contentResolver.openInputStream(it) } }
    }
    // A câmera grava sempre no mesmo arquivo da pasta de cache, entregue pelo FileProvider do módulo;
    // criado só ao tirar a foto (no Robolectric o FileProvider guardaria a pasta da primeira captura).
    val destino by remember(contexto) { lazy {
        val arquivo = File(contexto.cacheDir, "fotos").apply { mkdirs() }.resolve("foto.jpg")
        FileProvider.getUriForFile(contexto, "${contexto.packageName}.fotos.camera", arquivo)
    } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { tirou ->
        if (tirou) viewModel.enviar { contexto.contentResolver.openInputStream(destino) }
    }
    val temCamera = remember(contexto) { contexto.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    TelaDasFotos(
        estado,
        EventosDasFotos(
            voltar = voltar,
            abrirPremium = abrirPremium,
            tentarDeNovo = viewModel::tentarDeNovo,
            mudarMes = viewModel::mudarMes,
            escolherDaGaleria = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            tirarFoto = if (temCamera) ({ camera.launch(destino) }) else null,
            mudarLegenda = viewModel::mudarLegenda,
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
fun TelaDasFotos(estado: EstadoDasFotos, eventos: EventosDasFotos, modifier: Modifier = Modifier) {
    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fotos_titulo), Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(eventos.voltar) { Icon(painterResource(Icone.Esquerda.desenho), stringResource(Comum.string.voltar)) }
                },
            )
        },
    ) { margens ->
        Column(Modifier.padding(margens).fillMaxSize()) {
            estado.erro?.let { Faixa(it.texto(), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) }
            estado.guardadoEm?.let { AvisoDeGuardado(it, estado.conectado) }
            SeletorDeMes(estado.mes, estado.mesAtual, eventos.mudarMes, Modifier.align(Alignment.CenterHorizontally))
            // Duas colunas no celular; com a fonte grande do sistema, uma (como o iOS).
            val largura = if (LocalDensity.current.fontScale >= 1.5f) 280.dp else 160.dp
            LazyVerticalGrid(
                GridCells.Adaptive(largura),
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { Envio(estado, eventos) }
                when (val carga = estado.fotos) {
                    Carga.Carregando -> item(span = { GridItemSpan(maxLineSpan) }) { Esqueleto(linhas = 4) }
                    is Carga.Falhou -> item(span = { GridItemSpan(maxLineSpan) }) {
                        EstadoDeErro(falhaDe(carga.erro), eventos.tentarDeNovo, mensagem = mensagemDeErro(carga.erro).texto())
                    }
                    is Carga.Pronto -> if (carga.valor.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Vazio(Icone.Imagem, stringResource(R.string.fotos_vazio_titulo), texto = stringResource(R.string.fotos_vazio_texto))
                        }
                    } else {
                        items(carga.valor, key = { it.id }) { CartaoDaFoto(it, estado, eventos) }
                    }
                }
            }
        }
    }
    estado.apagando?.let {
        AlertDialog(
            onDismissRequest = eventos.desistir,
            confirmButton = {
                TextButton(eventos.confirmar) { Text(stringResource(R.string.fotos_apagar), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(eventos.desistir) { Text(stringResource(Comum.string.cancelar)) } },
            text = { Text(stringResource(R.string.fotos_apagar_pergunta)) },
        )
    }
}

/** Galeria e câmera para o Premium; para os outros, o convite (o link "Adicionar fotos · Premium" do site). */
@Composable
private fun Envio(estado: EstadoDasFotos, eventos: EventosDasFotos) {
    Card(Modifier.fillMaxWidth()) {
        FlowRow(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                !estado.premium -> OutlinedButton(eventos.abrirPremium) {
                    Icon(painterResource(Icone.Camera.desenho), null, Modifier.size(18.dp))
                    Text(stringResource(R.string.fotos_premium), Modifier.padding(start = 8.dp))
                }
                estado.enviando -> Row(
                    Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(R.string.fotos_enviando))
                }
                else -> {
                    val galeria = stringResource(R.string.fotos_adicionar_descricao)
                    OutlinedButton(eventos.escolherDaGaleria, Modifier.semantics { contentDescription = galeria }) {
                        Icon(painterResource(Icone.Imagem.desenho), null, Modifier.size(18.dp))
                        Text(stringResource(R.string.fotos_adicionar), Modifier.padding(start = 8.dp))
                    }
                    eventos.tirarFoto?.let { tirar ->
                        val camera = stringResource(R.string.fotos_tirar_descricao)
                        OutlinedButton(tirar, Modifier.semantics { contentDescription = camera }) {
                            Icon(painterResource(Icone.Camera.desenho), null, Modifier.size(18.dp))
                            Text(stringResource(R.string.fotos_tirar), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartaoDaFoto(foto: FotoDoFeed, estado: EstadoDasFotos, eventos: EventosDasFotos) {
    val foco = LocalFocusManager.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ImagemDoAsset(
                foto.assetId,
                foto.legenda?.takeIf { it.isNotBlank() } ?: stringResource(R.string.fotos_foto_do_mes),
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val descricao = stringResource(R.string.fotos_legenda_descricao)
                OutlinedTextField(
                    estado.legendaDe(foto),
                    { eventos.mudarLegenda(foto, it) },
                    Modifier.weight(1f).semantics { contentDescription = descricao },
                    placeholder = { Text(stringResource(R.string.fotos_legenda)) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    // A coluna é estreita no celular: a legenda quebra a linha em vez de sumir.
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { foco.clearFocus() }),
                )
                IconButton({ eventos.pedirParaApagar(foto) }) {
                    Icon(painterResource(Icone.Lixeira.desenho), stringResource(R.string.fotos_apagar), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
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

/** Por que o mural não carregou, para o ícone e o texto do [EstadoDeErro]. */
internal fun falhaDe(erro: ErroDaApi): FalhaAoCarregar = when {
    erro is ErroDaApi.SemInternet -> FalhaAoCarregar.SemInternet
    erro is ErroDaApi.DoServidor && erro.status >= 500 -> FalhaAoCarregar.DoServidor
    else -> FalhaAoCarregar.Outra
}
