package br.com.piggu.feature.listas.compras

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.model.ItemDeCompra
import br.com.piggu.core.model.ListaDeCompras
import br.com.piggu.core.model.ProdutoDoCatalogo
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.AvisoDeGuardado
import br.com.piggu.core.ui.EstadoDeErro
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.FalhaAoCarregar
import br.com.piggu.core.ui.Vazio
import br.com.piggu.feature.listas.R
import coil3.compose.AsyncImage
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import br.com.piggu.core.ui.R as Comum

data class EventosDasCompras(
    val voltar: () -> Unit = {},
    val tentarDeNovo: () -> Unit = {},
    val trocarLista: (ListaDeCompras) -> Unit = {},
    val mudarItem: (String) -> Unit = {},
    val mudarQuantidade: (String) -> Unit = {},
    val adicionar: () -> Unit = {},
    val mudarBusca: (String) -> Unit = {},
    val buscar: () -> Unit = {},
    /** Abre o leitor de código de barras; nulo esconde o botão (prévias e capturas). */
    val lerCodigo: (() -> Unit)? = null,
    val adicionarDoCatalogo: (ProdutoDoCatalogo) -> Unit = {},
    val alternarComprado: (ItemDeCompra) -> Unit = {},
    val excluir: (ItemDeCompra) -> Unit = {},
)

/** Compras ligada ao [ComprasViewModel]: tela cheia, aberta pelo Mais. */
@Composable
fun TelaDeCompras(voltar: () -> Unit, modifier: Modifier = Modifier, viewModel: ComprasViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    // O leitor do Google Play services abre a câmera dele: o app não pede permissão de câmera.
    // Criado só no toque: o ML Kit nem sempre está pronto ao abrir a tela (e não está no Robolectric).
    val leitor by remember(contexto) { lazy {
        val formatos = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build()
        GmsBarcodeScanning.getClient(contexto, formatos)
    } }
    TelaDeCompras(
        estado,
        EventosDasCompras(
            voltar = voltar,
            tentarDeNovo = viewModel::tentarDeNovo,
            trocarLista = viewModel::trocarLista,
            mudarItem = viewModel::mudarItem,
            mudarQuantidade = viewModel::mudarQuantidade,
            adicionar = viewModel::adicionar,
            mudarBusca = viewModel::mudarBusca,
            buscar = viewModel::buscar,
            // Cancelar não é erro; falhar (sem Play services, sem baixar o módulo) avisa e a busca digitada segue.
            lerCodigo = {
                leitor.startScan()
                    .addOnSuccessListener { codigo -> codigo.rawValue?.let(viewModel::codigoLido) }
                    .addOnFailureListener { viewModel.leitorIndisponivel() }
            },
            adicionarDoCatalogo = viewModel::adicionarDoCatalogo,
            alternarComprado = viewModel::alternarComprado,
            excluir = viewModel::excluir,
        ),
        modifier,
    )
}

/** A tela só desenhada (capturas e prévias). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDeCompras(estado: EstadoDasCompras, eventos: EventosDasCompras, modifier: Modifier = Modifier) {
    val focoNoItem = remember { FocusRequester() }
    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compras_titulo)) },
                navigationIcon = {
                    IconButton(eventos.voltar) { Icon(painterResource(Icone.Esquerda.desenho), stringResource(Comum.string.voltar)) }
                },
            )
        },
    ) { margens ->
        Column(Modifier.padding(margens).fillMaxSize().wrapContentWidth().widthIn(max = 720.dp)) {
            estado.erro?.let { Faixa(it.texto()) }
            estado.guardadoEm?.let { AvisoDeGuardado(it, estado.conectado) }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Formulario(estado, eventos, focoNoItem) }
                when (val carga = estado.carga) {
                    Carga.Carregando -> item { Esqueleto(linhas = 4) }
                    is Carga.Falhou -> item {
                        EstadoDeErro(falhaDe(carga.erro), eventos.tentarDeNovo, mensagem = mensagemDeErro(carga.erro).texto())
                    }
                    is Carga.Pronto -> {
                        item {
                            Cartao(stringResource(R.string.compras_a_comprar), estado.pendentes.size) {
                                if (estado.pendentes.isEmpty()) {
                                    Vazio(
                                        Icone.Carrinho,
                                        stringResource(R.string.compras_vazio_titulo),
                                        texto = stringResource(R.string.compras_vazio_texto),
                                        compacto = true,
                                        acao = stringResource(R.string.compras_vazio_acao) to { focoNoItem.requestFocus() },
                                    )
                                }
                                estado.pendentes.forEach { LinhaDoItem(it, eventos) }
                            }
                        }
                        if (estado.comprados.isNotEmpty()) {
                            item {
                                Cartao(stringResource(R.string.compras_no_carrinho), estado.comprados.size) {
                                    estado.comprados.forEach { LinhaDoItem(it, eventos) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Formulario(estado: EstadoDasCompras, eventos: EventosDasCompras, focoNoItem: FocusRequester) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val listas = listOf(ListaDeCompras.COMPRAS to R.string.compras_aba_compras, ListaDeCompras.DESEJOS to R.string.compras_aba_desejos)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listas.forEachIndexed { i, (lista, rotulo) ->
                    SegmentedButton(estado.lista == lista, { eventos.trocarLista(lista) }, SegmentedButtonDefaults.itemShape(i, listas.size)) {
                        Text(stringResource(rotulo))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    estado.item,
                    eventos.mudarItem,
                    Modifier.weight(1f).focusRequester(focoNoItem),
                    label = { Text(stringResource(R.string.compras_item)) },
                    placeholder = { Text(stringResource(R.string.compras_item_exemplo)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    estado.quantidade,
                    eventos.mudarQuantidade,
                    Modifier.weight(1f),
                    label = { Text(stringResource(R.string.compras_quantidade)) },
                    placeholder = { Text(stringResource(R.string.compras_quantidade_exemplo)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { eventos.adicionar() }),
                )
            }
            Button(eventos.adicionar, Modifier.fillMaxWidth()) { Text(stringResource(R.string.compras_adicionar)) }
            OutlinedTextField(
                estado.busca,
                eventos.mudarBusca,
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.compras_buscar_no_catalogo)) },
                placeholder = { Text(stringResource(R.string.compras_busca_exemplo)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { eventos.buscar() }),
                trailingIcon = eventos.lerCodigo?.let { ler ->
                    {
                        IconButton(ler, enabled = !estado.buscando) {
                            Icon(painterResource(Icone.Camera.desenho), stringResource(R.string.compras_ler_codigo))
                        }
                    }
                },
            )
            OutlinedButton(eventos.buscar, Modifier.fillMaxWidth(), enabled = !estado.buscando) {
                Text(stringResource(if (estado.buscando) R.string.compras_buscando else R.string.compras_buscar))
            }
            estado.resultados.forEach { ResultadoDoCatalogo(it, eventos) }
        }
    }
}

@Composable
private fun ResultadoDoCatalogo(produto: ProdutoDoCatalogo, eventos: EventosDasCompras) {
    val rotulo = stringResource(R.string.compras_adicionar_produto, produto.nome)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = rotulo) { eventos.adicionarDoCatalogo(produto) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Miniatura(produto.imagem)
        Column(Modifier.weight(1f)) {
            Text(produto.nome, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            detalhe(produto.marca, produto.quantidade)?.let { Suave(it) }
        }
        Icon(painterResource(Icone.Mais.desenho), null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** Marcar e desmarcar a linha inteira, como o label com checkbox do site; apagar no ícone ao lado. */
@Composable
private fun LinhaDoItem(item: ItemDeCompra, eventos: EventosDasCompras) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp)
                .toggleable(item.comprado, role = Role.Checkbox) { eventos.alternarComprado(item) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(item.comprado, onCheckedChange = null)
            // Como no site, o que já está no carrinho fica só com o nome riscado.
            if (!item.comprado && !item.imagem.isNullOrEmpty()) Miniatura(item.imagem)
            Column {
                Text(
                    item.item,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.comprado) TextDecoration.LineThrough else null,
                    color = if (item.comprado) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                if (!item.comprado) detalhe(item.marca, item.quantidade)?.let { Suave(it) }
            }
        }
        IconButton({ eventos.excluir(item) }) {
            Icon(painterResource(Icone.Lixeira.desenho), stringResource(R.string.compras_apagar_de, item.item), tint = MaterialTheme.colorScheme.error)
        }
    }
}

/** "Marca 1 L": o que houver dos dois; nulo quando não há nada. */
private fun detalhe(marca: String?, quantidade: String?): String? =
    listOfNotNull(marca, quantidade).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { null }

/** A foto do catálogo (URL pública por HTTPS) num quadrado; sem foto, o carrinho. */
@Composable
private fun Miniatura(url: String?) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrEmpty()) {
            Icon(painterResource(Icone.Carrinho.desenho), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        } else {
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun Cartao(titulo: String, quantos: Int, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
                Text(titulo, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(quantos.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            conteudo()
        }
    }
}

@Composable
private fun Suave(texto: String) =
    Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Faixa(texto: String) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
        Text(
            texto,
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp).semantics { liveRegion = LiveRegionMode.Assertive },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
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
