package br.com.piggu.feature.listas.lugares

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.model.Lugar
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.AvisoDeGuardado
import br.com.piggu.core.ui.CampoDeValor
import br.com.piggu.core.ui.EstadoDeErro
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.FalhaAoCarregar
import br.com.piggu.core.ui.ImagemDoAsset
import br.com.piggu.core.ui.Vazio
import br.com.piggu.core.ui.formatos.dataCurta
import br.com.piggu.core.ui.formatos.formatarMoeda
import br.com.piggu.core.ui.formatos.simboloDaMoeda
import br.com.piggu.feature.listas.Estrelas
import br.com.piggu.feature.listas.R
import coil3.compose.AsyncImage
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64
import br.com.piggu.core.ui.R as Comum

data class EventosDosLugares(
    val voltar: () -> Unit = {},
    val tentarDeNovo: () -> Unit = {},
    val abrirNovo: () -> Unit = {},
    val editar: (Lugar) -> Unit = {},
    val fechar: () -> Unit = {},
    val mudarNome: (String) -> Unit = {},
    val mudarCategoria: (String) -> Unit = {},
    val mudarLocalizacao: (String) -> Unit = {},
    val mudarData: (String) -> Unit = {},
    val mudarValor: (BigDecimal?) -> Unit = {},
    val mudarNota: (Int) -> Unit = {},
    val mudarComentario: (String) -> Unit = {},
    val alternarMarcador: (String) -> Unit = {},
    val mudarNovoMarcador: (String) -> Unit = {},
    val criarMarcador: () -> Unit = {},
    /** Abre o Photo Picker. */
    val escolherDaGaleria: () -> Unit = {},
    /** Abre a câmera; nulo esconde o botão (aparelho sem câmera). */
    val tirarFoto: (() -> Unit)? = null,
    val salvar: () -> Unit = {},
    val abrirNoMapa: (Lugar) -> Unit = {},
    val pedirParaApagar: (Lugar) -> Unit = {},
    val confirmar: () -> Unit = {},
    val desistir: () -> Unit = {},
)

/** Lugares ligada ao [LugaresViewModel]: tela cheia, aberta pelo Mais. */
@Composable
fun TelaDeLugares(voltar: () -> Unit, modifier: Modifier = Modifier) {
    TelaDeLugares(voltar, modifier, hiltViewModel())
}

@Composable
internal fun TelaDeLugares(voltar: () -> Unit, modifier: Modifier, viewModel: LugaresViewModel) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.escolherFoto { contexto.contentResolver.openInputStream(it) } }
    }
    // A câmera grava sempre no mesmo arquivo da pasta de cache, entregue pelo FileProvider do módulo;
    // criado só ao tirar a foto.
    val destino by remember(contexto) { lazy {
        val arquivo = File(contexto.cacheDir, "lugares").apply { mkdirs() }.resolve("foto.jpg")
        FileProvider.getUriForFile(contexto, "${contexto.packageName}.lugares.fotos", arquivo)
    } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { tirou ->
        if (tirou) viewModel.escolherFoto { contexto.contentResolver.openInputStream(destino) }
    }
    val temCamera = remember(contexto) { contexto.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    TelaDeLugares(
        estado,
        EventosDosLugares(
            voltar = voltar,
            tentarDeNovo = viewModel::tentarDeNovo,
            abrirNovo = viewModel::abrirNovo,
            editar = viewModel::editar,
            fechar = viewModel::fechar,
            mudarNome = viewModel::mudarNome,
            mudarCategoria = viewModel::mudarCategoria,
            mudarLocalizacao = viewModel::mudarLocalizacao,
            mudarData = viewModel::mudarData,
            mudarValor = viewModel::mudarValor,
            mudarNota = viewModel::mudarNota,
            mudarComentario = viewModel::mudarComentario,
            alternarMarcador = viewModel::alternarMarcador,
            mudarNovoMarcador = viewModel::mudarNovoMarcador,
            criarMarcador = viewModel::criarMarcador,
            escolherDaGaleria = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            tirarFoto = if (temCamera) ({ camera.launch(destino) }) else null,
            salvar = viewModel::salvar,
            // Sem chave do Google Maps ainda: o app de mapas do aparelho procura o lugar.
            abrirNoMapa = { lugar ->
                enderecoNoMapa(lugar)?.let { endereco ->
                    try {
                        contexto.startActivity(Intent(Intent.ACTION_VIEW, endereco.toUri()))
                    } catch (_: ActivityNotFoundException) {
                        viewModel.semAppDeMapa()
                    }
                }
            },
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
fun TelaDeLugares(estado: EstadoDosLugares, eventos: EventosDosLugares, modifier: Modifier = Modifier) {
    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lugares_titulo), Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(eventos.voltar) { Icon(painterResource(Icone.Esquerda.desenho), stringResource(Comum.string.voltar)) }
                },
                actions = {
                    IconButton(eventos.abrirNovo) { Icon(painterResource(Icone.Mais.desenho), stringResource(R.string.lugares_novo)) }
                },
            )
        },
    ) { margens ->
        Column(Modifier.padding(margens).fillMaxSize().wrapContentWidth().widthIn(max = 720.dp)) {
            estado.erro?.let { Faixa(it.texto(), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) }
            estado.guardadoEm?.let { AvisoDeGuardado(it, estado.conectado) }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (val carga = estado.carga) {
                    Carga.Carregando -> item { Esqueleto(linhas = 4) }
                    is Carga.Falhou -> item {
                        EstadoDeErro(falhaDe(carga.erro), eventos.tentarDeNovo, mensagem = mensagemDeErro(carga.erro).texto())
                    }
                    is Carga.Pronto -> if (carga.valor.lugares.isEmpty()) {
                        item {
                            Vazio(
                                Icone.Local,
                                stringResource(R.string.lugares_vazio_titulo),
                                texto = stringResource(R.string.lugares_vazio_texto),
                                acao = stringResource(R.string.lugares_vazio_acao) to eventos.abrirNovo,
                            )
                        }
                    } else {
                        items(carga.valor.lugares, key = { it.id }) { CartaoDoLugar(it, estado.moeda, eventos) }
                    }
                }
            }
        }
    }
    if (estado.formulario.aberto) FormularioDoLugar(estado, eventos)
    estado.apagando?.let { lugar ->
        AlertDialog(
            onDismissRequest = eventos.desistir,
            confirmButton = {
                TextButton(eventos.confirmar) { Text(stringResource(R.string.lugares_apagar), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(eventos.desistir) { Text(stringResource(Comum.string.cancelar)) } },
            text = { Text(stringResource(R.string.lugares_apagar_pergunta, lugar.nome)) },
        )
    }
}

@Composable
private fun CartaoDoLugar(lugar: Lugar, moeda: String, eventos: EventosDosLugares) {
    Card(Modifier.fillMaxWidth()) {
        lugar.fotoAssetId?.takeIf { lugar.temFoto == true }?.let {
            ImagemDoAsset(it, lugar.nome, Modifier.fillMaxWidth().height(160.dp))
        }
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.padding(end = 12.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(lugar.nome, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(lugar.categoria, lugar.localizacao, dataCurta(lugar.data)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val nota = stringResource(R.string.lugares_nota_de_5, lugar.nota)
                Text(
                    "★".repeat(lugar.nota.coerceIn(0, 5)),
                    Modifier.semantics { contentDescription = nota },
                    color = MaterialTheme.colorScheme.tertiary,
                )
                lugar.comentario?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (lugar.valor.signum() > 0) {
                    Text(formatarMoeda(lugar.valor, moeda), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                if (lugar.marcacoes.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        lugar.marcacoes.forEach { Etiqueta(it) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (enderecoNoMapa(lugar) != null) {
                    val mapa = stringResource(R.string.lugares_abrir_no_mapa_de, lugar.nome)
                    TextButton({ eventos.abrirNoMapa(lugar) }, Modifier.semantics { contentDescription = mapa }) {
                        Text(stringResource(R.string.lugares_abrir_no_mapa))
                    }
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    IconButton({ eventos.editar(lugar) }) {
                        Icon(painterResource(Icone.Lapis.desenho), stringResource(R.string.lugares_editar_de, lugar.nome))
                    }
                    IconButton({ eventos.pedirParaApagar(lugar) }) {
                        Icon(painterResource(Icone.Lixeira.desenho), stringResource(R.string.lugares_apagar_de, lugar.nome), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** Novo lugar ou editar um, numa folha (no site é um cartão acima da lista). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormularioDoLugar(estado: EstadoDosLugares, eventos: EventosDosLugares) {
    val f = estado.formulario
    ModalBottomSheet(eventos.fechar) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (f.editando == null) R.string.lugares_novo else R.string.lugares_editar),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                f.nome,
                eventos.mudarNome,
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.lugares_nome)) },
                singleLine = true,
                isError = f.faltou == CampoDoLugar.Nome,
                supportingText = f.erro?.takeIf { f.faltou == CampoDoLugar.Nome }?.let { erro -> { Text(erro.texto()) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                f.categoria,
                eventos.mudarCategoria,
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.lugares_categoria)) },
                placeholder = { Text(stringResource(R.string.lugares_categoria_exemplo)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                f.localizacao,
                eventos.mudarLocalizacao,
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.lugares_localizacao)) },
                placeholder = { Text(stringResource(R.string.lugares_localizacao_exemplo)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            CampoDeData(stringResource(R.string.lugares_data), f.data, eventos.mudarData, Modifier.fillMaxWidth())
            CampoDeValor(
                f.valor,
                eventos.mudarValor,
                estado.moeda,
                Modifier.fillMaxWidth(),
                rotulo = stringResource(R.string.lugares_valor, simboloDaMoeda(estado.moeda)),
            )
            Text(stringResource(R.string.lugares_nota), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
            Estrelas(f.nota, eventos.mudarNota, { stringResource(R.string.lugares_nota_n, it) })
            OutlinedTextField(
                f.comentario,
                eventos.mudarComentario,
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.lugares_comentario)) },
                minLines = 2,
                maxLines = 5,
            )
            Text(stringResource(R.string.lugares_marcadores), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
            val marcadores = estado.dados?.marcadores.orEmpty()
            if (marcadores.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    marcadores.forEach { marcador ->
                        FilterChip(marcador in f.marcacoes, { eventos.alternarMarcador(marcador) }, label = { Text(marcador) })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    f.novoMarcador,
                    eventos.mudarNovoMarcador,
                    Modifier.weight(1f),
                    label = { Text(stringResource(R.string.lugares_criar_marcador)) },
                    singleLine = true,
                    isError = f.faltou == CampoDoLugar.Marcador,
                    supportingText = f.erro?.takeIf { f.faltou == CampoDoLugar.Marcador }?.let { erro -> { Text(erro.texto()) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { eventos.criarMarcador() }),
                )
                OutlinedButton(eventos.criarMarcador) { Text(stringResource(R.string.lugares_adicionar_marcador)) }
            }
            Foto(f, eventos)
            // O erro do nome e do marcador aparece no campo; os outros (foto, servidor), aqui.
            f.erro?.takeIf { f.faltou == null }?.let {
                Text(it.texto(), Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, color = MaterialTheme.colorScheme.error)
            }
            Button(eventos.salvar, Modifier.fillMaxWidth(), enabled = !f.salvando && !f.lendoFoto) {
                Text(stringResource(if (f.salvando) R.string.lugares_guardando else R.string.lugares_salvar))
            }
            OutlinedButton(eventos.fechar, Modifier.fillMaxWidth()) { Text(stringResource(Comum.string.cancelar)) }
        }
    }
}

/** A foto nova escolhida, ou a que o lugar já tem; e os botões da galeria e da câmera. */
@Composable
private fun Foto(f: FormularioDeLugar, eventos: EventosDosLugares) {
    val descricao = stringResource(R.string.lugares_foto_do_lugar)
    val forma = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp))
    val nova = f.foto?.let { remember(it) { Base64.getDecoder().decode(it) } }
    val antiga = f.editando?.takeIf { it.temFoto == true }?.fotoAssetId
    when {
        nova != null -> AsyncImage(nova, descricao, forma, contentScale = ContentScale.Crop)
        antiga != null -> ImagemDoAsset(antiga, descricao, forma)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val galeria = stringResource(R.string.lugares_galeria_descricao)
        OutlinedButton(eventos.escolherDaGaleria, Modifier.semantics { contentDescription = galeria }, enabled = !f.lendoFoto) {
            Text(stringResource(R.string.lugares_escolher_foto))
        }
        eventos.tirarFoto?.let { tirar ->
            val camera = stringResource(R.string.lugares_camera_descricao)
            OutlinedButton(tirar, Modifier.semantics { contentDescription = camera }, enabled = !f.lendoFoto) {
                Text(stringResource(R.string.lugares_camera))
            }
        }
    }
}

/**
 * Data (aaaa-mm-dd) num campo só de leitura que abre o calendário. O DatePicker trabalha em
 * milissegundos UTC: por isso a conversão por UTC, para não deslocar o dia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampoDeData(rotulo: String, data: String, aoMudar: (String) -> Unit, modifier: Modifier = Modifier) {
    var aberto by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        dataCurta(data),
        {},
        modifier,
        readOnly = true,
        label = { Text(rotulo) },
        singleLine = true,
        trailingIcon = {
            IconButton({ aberto = true }) { Icon(painterResource(Icone.Calendario.desenho), stringResource(R.string.lugares_escolher_data)) }
        },
    )
    if (!aberto) return
    val dia = runCatching { LocalDate.parse(data) }.getOrNull()
    val calendario = rememberDatePickerState(dia?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
    DatePickerDialog(
        onDismissRequest = { aberto = false },
        confirmButton = {
            TextButton({
                calendario.selectedDateMillis?.let { aoMudar(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                aberto = false
            }) { Text(stringResource(R.string.lugares_ok)) }
        },
        dismissButton = { TextButton({ aberto = false }) { Text(stringResource(Comum.string.cancelar)) } },
    ) { DatePicker(calendario) }
}

@Composable
private fun Etiqueta(texto: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
        Text(texto, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
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
