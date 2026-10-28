package br.com.piggu.feature.girlsmath

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.model.ItemDoGirlsMath
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.AvisoDeGuardado
import br.com.piggu.core.ui.CampoDeValor
import br.com.piggu.core.ui.EstadoDeErro
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.FalhaAoCarregar
import br.com.piggu.core.ui.Vazio
import br.com.piggu.core.ui.formatos.dataCurta
import br.com.piggu.core.ui.formatos.formatarMoeda
import br.com.piggu.core.ui.formatos.simboloDaMoeda
import java.io.File
import java.math.BigDecimal
import java.util.Base64
import br.com.piggu.core.ui.R as Comum

data class EventosDoGirlsMath(
    val voltar: () -> Unit = {},
    val tentarDeNovo: () -> Unit = {},
    val mudarProduto: (String) -> Unit = {},
    val mudarLoja: (String) -> Unit = {},
    val mudarValor: (BigDecimal?) -> Unit = {},
    /** Abre o Photo Picker. */
    val escolherDaGaleria: () -> Unit = {},
    /** Abre a câmera; nulo esconde o botão (aparelho sem câmera). */
    val tirarFoto: (() -> Unit)? = null,
    val removerFoto: () -> Unit = {},
    val guardar: () -> Unit = {},
    val pedirParaApagar: (ItemDoGirlsMath) -> Unit = {},
    val confirmar: () -> Unit = {},
    val desistir: () -> Unit = {},
)

/** Girls Math ligada ao [GirlsMathViewModel]: tela cheia, aberta pelo Mais. */
@Composable
fun TelaDoGirlsMath(voltar: () -> Unit, modifier: Modifier = Modifier, viewModel: GirlsMathViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.escolherFoto { contexto.contentResolver.openInputStream(it) } }
    }
    // A câmera grava sempre no mesmo arquivo da pasta de cache, entregue pelo FileProvider do módulo;
    // criado só ao tirar a foto.
    val destino by remember(contexto) { lazy {
        val arquivo = File(contexto.cacheDir, "girlsmath").apply { mkdirs() }.resolve("foto.jpg")
        FileProvider.getUriForFile(contexto, "${contexto.packageName}.girlsmath.fotos", arquivo)
    } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { tirou ->
        if (tirou) viewModel.escolherFoto { contexto.contentResolver.openInputStream(destino) }
    }
    val temCamera = remember(contexto) { contexto.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    TelaDoGirlsMath(
        estado,
        EventosDoGirlsMath(
            voltar = voltar,
            tentarDeNovo = viewModel::tentarDeNovo,
            mudarProduto = viewModel::mudarProduto,
            mudarLoja = viewModel::mudarLoja,
            mudarValor = viewModel::mudarValor,
            escolherDaGaleria = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            tirarFoto = if (temCamera) ({ camera.launch(destino) }) else null,
            removerFoto = viewModel::removerFoto,
            guardar = viewModel::guardar,
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
fun TelaDoGirlsMath(estado: EstadoDoGirlsMath, eventos: EventosDoGirlsMath, modifier: Modifier = Modifier) {
    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.girlsmath_titulo)) },
                navigationIcon = {
                    IconButton(eventos.voltar) { Icon(painterResource(Icone.Esquerda.desenho), stringResource(Comum.string.voltar)) }
                },
            )
        },
    ) { margens ->
        Column(Modifier.padding(margens).fillMaxSize().wrapContentWidth().widthIn(max = 720.dp)) {
            // O erro do produto e do valor aparece no campo; os outros, na faixa.
            estado.erro?.takeIf { estado.faltou == null }?.let {
                Faixa(it.texto(), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            }
            estado.guardadoEm?.let { AvisoDeGuardado(it, estado.conectado) }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Text(stringResource(R.string.girlsmath_lead), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item { Placar(estado) }
                item { Formulario(estado, eventos) }
                when (val carga = estado.itens) {
                    Carga.Carregando -> item { Esqueleto(linhas = 4) }
                    is Carga.Falhou -> item {
                        EstadoDeErro(falhaDe(carga.erro), eventos.tentarDeNovo, mensagem = mensagemDeErro(carga.erro).texto())
                    }
                    is Carga.Pronto -> if (carga.valor.isEmpty()) {
                        item {
                            Vazio(Icone.Sacola, stringResource(R.string.girlsmath_vazio_titulo), texto = stringResource(R.string.girlsmath_vazio_texto))
                        }
                    } else {
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
                                    carga.valor.forEach { LinhaDoItem(it, estado, eventos) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    estado.apagando?.let { item ->
        AlertDialog(
            onDismissRequest = eventos.desistir,
            confirmButton = {
                TextButton(eventos.confirmar) { Text(stringResource(R.string.girlsmath_apagar), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(eventos.desistir) { Text(stringResource(Comum.string.cancelar)) } },
            text = { Text(stringResource(R.string.girlsmath_apagar_pergunta, item.produto)) },
        )
    }
}

@Composable
private fun Placar(estado: EstadoDoGirlsMath) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.girlsmath_este_mes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatarMoeda(estado.noMes, estado.moeda),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.girlsmath_desde_sempre, formatarMoeda(estado.total, estado.moeda)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Formulario(estado: EstadoDoGirlsMath, eventos: EventosDoGirlsMath) {
    Cartao(stringResource(R.string.girlsmath_novo)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val previa = estado.foto?.let { remember(it) { Base64.getDecoder().decode(it) } }
            Miniatura(
                previa,
                64.dp,
                Icone.Camera,
                stringResource(if (previa == null) R.string.girlsmath_sem_foto else R.string.girlsmath_foto_do_produto),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val galeria = stringResource(R.string.girlsmath_galeria_descricao)
                OutlinedButton(eventos.escolherDaGaleria, Modifier.semantics { contentDescription = galeria }, enabled = !estado.lendoFoto) {
                    Text(stringResource(R.string.girlsmath_galeria))
                }
                eventos.tirarFoto?.let { tirar ->
                    val camera = stringResource(R.string.girlsmath_camera_descricao)
                    OutlinedButton(tirar, Modifier.semantics { contentDescription = camera }, enabled = !estado.lendoFoto) {
                        Text(stringResource(R.string.girlsmath_camera))
                    }
                }
                if (estado.foto != null) {
                    TextButton(eventos.removerFoto) { Text(stringResource(R.string.girlsmath_tirar_a_foto), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        OutlinedTextField(
            estado.produto,
            eventos.mudarProduto,
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.girlsmath_produto)) },
            singleLine = true,
            isError = estado.faltou == CampoDoGirlsMath.Produto,
            supportingText = estado.erro?.takeIf { estado.faltou == CampoDoGirlsMath.Produto }?.let { erro -> { Text(erro.texto()) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            estado.loja,
            eventos.mudarLoja,
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.girlsmath_loja)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        CampoDeValor(
            estado.valor,
            eventos.mudarValor,
            estado.moeda,
            Modifier.fillMaxWidth(),
            rotulo = stringResource(R.string.girlsmath_valor, simboloDaMoeda(estado.moeda)),
            erro = estado.erro?.takeIf { estado.faltou == CampoDoGirlsMath.Valor }?.texto(),
        )
        Button(eventos.guardar, Modifier.fillMaxWidth(), enabled = !estado.salvando && !estado.lendoFoto) {
            Text(stringResource(if (estado.salvando) R.string.girlsmath_guardando else R.string.girlsmath_guardar))
        }
    }
}

@Composable
private fun LinhaDoItem(item: ItemDoGirlsMath, estado: EstadoDoGirlsMath, eventos: EventosDoGirlsMath) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Miniatura(item.fotoAssetId?.let { estado.miniaturas[it] }, 44.dp, Icone.Sacola, null)
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(item.produto, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(dataCurta(item.data), item.loja?.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(formatarMoeda(item.valor, estado.moeda), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        IconButton({ eventos.pedirParaApagar(item) }) {
            Icon(painterResource(Icone.Lixeira.desenho), stringResource(R.string.girlsmath_apagar_de, item.produto), tint = MaterialTheme.colorScheme.error)
        }
    }
}

/** A foto num quadrado arredondado; sem foto, ou foto que o Android não lê (SVG da demo), o [marcador]. */
@Composable
private fun Miniatura(bytes: ByteArray?, lado: Dp, marcador: Icone, descricao: String?) {
    val ladoEmPx = with(LocalDensity.current) { lado.roundToPx() }
    val imagem = bytes?.let { remember(it, ladoEmPx) { lerParaATela(it, ladoEmPx)?.asImageBitmap() } }
    Box(
        Modifier.size(lado).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer)
            .then(if (descricao != null) Modifier.semantics { contentDescription = descricao } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (imagem != null) {
            Image(imagem, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(painterResource(marcador.desenho), null, Modifier.size(lado / 2.5f), tint = MaterialTheme.colorScheme.primary)
        }
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
