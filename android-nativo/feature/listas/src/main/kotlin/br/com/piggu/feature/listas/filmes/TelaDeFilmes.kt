package br.com.piggu.feature.listas.filmes

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.model.Filme
import br.com.piggu.core.model.FilmeDoCatalogo
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.AvisoDeGuardado
import br.com.piggu.core.ui.EstadoDeErro
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.FalhaAoCarregar
import br.com.piggu.core.ui.Vazio
import br.com.piggu.feature.listas.Estrelas
import br.com.piggu.feature.listas.R
import coil3.compose.AsyncImage
import java.math.BigDecimal
import java.util.Locale
import br.com.piggu.core.ui.R as Comum

data class EventosDosFilmes(
    val voltar: () -> Unit = {},
    val tentarDeNovo: () -> Unit = {},
    val mudarGenero: (String) -> Unit = {},
    val sortear: () -> Unit = {},
    val mudarBusca: (String) -> Unit = {},
    val buscar: () -> Unit = {},
    val adicionar: (FilmeDoCatalogo) -> Unit = {},
    val alternarAssistido: (Filme) -> Unit = {},
    val avaliar: (Filme, Int) -> Unit = { _, _ -> },
    val pedirParaTirar: (Filme) -> Unit = {},
    val confirmar: () -> Unit = {},
    val desistir: () -> Unit = {},
)

/** Filmes ligada ao [FilmesViewModel]: tela cheia, aberta pelo Mais. */
@Composable
fun TelaDeFilmes(voltar: () -> Unit, modifier: Modifier = Modifier) {
    TelaDeFilmes(voltar, modifier, hiltViewModel())
}

@Composable
internal fun TelaDeFilmes(voltar: () -> Unit, modifier: Modifier, viewModel: FilmesViewModel) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    TelaDeFilmes(
        estado,
        EventosDosFilmes(
            voltar = voltar,
            tentarDeNovo = viewModel::tentarDeNovo,
            mudarGenero = viewModel::mudarGenero,
            sortear = viewModel::sortear,
            mudarBusca = viewModel::mudarBusca,
            buscar = viewModel::buscar,
            adicionar = viewModel::adicionar,
            alternarAssistido = viewModel::alternarAssistido,
            avaliar = viewModel::avaliar,
            pedirParaTirar = viewModel::pedirParaTirar,
            confirmar = viewModel::confirmar,
            desistir = viewModel::desistir,
        ),
        modifier,
    )
}

/** A tela só desenhada (capturas e prévias). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDeFilmes(estado: EstadoDosFilmes, eventos: EventosDosFilmes, modifier: Modifier = Modifier) {
    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.filmes_titulo), Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(eventos.voltar) { Icon(painterResource(Icone.Esquerda.desenho), stringResource(Comum.string.voltar)) }
                },
            )
        },
    ) { margens ->
        Column(Modifier.padding(margens).fillMaxSize().wrapContentWidth().widthIn(max = 720.dp)) {
            estado.erro?.let { Faixa(it.texto(), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) }
            estado.guardadoEm?.let { AvisoDeGuardado(it, estado.conectado) }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { OQueAssistir(estado, eventos) }
                when (val carga = estado.carga) {
                    Carga.Carregando -> item { Esqueleto(linhas = 4) }
                    is Carga.Falhou -> item {
                        EstadoDeErro(falhaDe(carga.erro), eventos.tentarDeNovo, mensagem = mensagemDeErro(carga.erro).texto())
                    }
                    is Carga.Pronto -> {
                        item { ParaAssistir(estado, eventos) }
                        item { JaAssistidos(estado, eventos) }
                    }
                }
            }
        }
    }
    estado.tirando?.let { filme ->
        AlertDialog(
            onDismissRequest = eventos.desistir,
            confirmButton = {
                TextButton(eventos.confirmar) { Text(stringResource(R.string.filmes_tirar), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(eventos.desistir) { Text(stringResource(Comum.string.cancelar)) } },
            text = { Text(stringResource(R.string.filmes_tirar_pergunta, filme.titulo)) },
        )
    }
}

/** Sortear por gênero e buscar no catálogo. */
@Composable
private fun OQueAssistir(estado: EstadoDosFilmes, eventos: EventosDosFilmes) {
    Cartao(stringResource(R.string.filmes_o_que_assistir)) {
        Text(stringResource(R.string.filmes_genero), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GENEROS.forEach { genero ->
                FilterChip(estado.genero == genero.id, { eventos.mudarGenero(genero.id) }, label = { Text(stringResource(genero.nome)) })
            }
        }
        Button(eventos.sortear, Modifier.fillMaxWidth(), enabled = !estado.sorteando) {
            Text(stringResource(if (estado.sorteando) R.string.filmes_sorteando else R.string.filmes_sortear))
        }
        estado.sorteado?.let { DoCatalogo(it, destaque = true, eventos) }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                estado.busca,
                eventos.mudarBusca,
                Modifier.weight(1f),
                label = { Text(stringResource(R.string.filmes_buscar_filme)) },
                placeholder = { Text(stringResource(R.string.filmes_nome_do_filme)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { eventos.buscar() }),
            )
            OutlinedButton(eventos.buscar, enabled = !estado.buscando) {
                Text(stringResource(if (estado.buscando) R.string.filmes_buscando else R.string.filmes_buscar))
            }
        }
        estado.resultados.forEach { DoCatalogo(it, destaque = false, eventos) }
    }
}

/** Um filme do catálogo, sorteado ([destaque], com a sinopse) ou achado na busca. */
@Composable
private fun DoCatalogo(filme: FilmeDoCatalogo, destaque: Boolean, eventos: EventosDosFilmes) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Poster(filme.poster, filme.titulo, if (destaque) 80.dp else 52.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    listOfNotNull(filme.titulo, filme.ano?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall,
                )
                filme.nota?.let {
                    Text(
                        stringResource(R.string.filmes_nota_tmdb, notaDoTmdb(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                filme.sinopse?.takeIf { destaque && it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 4)
                }
            }
            val adicionar = stringResource(R.string.filmes_adicionar_de, filme.titulo)
            OutlinedButton({ eventos.adicionar(filme) }, Modifier.semantics { contentDescription = adicionar }) {
                Text(stringResource(if (destaque) R.string.filmes_adicionar_a_lista else R.string.filmes_adicionar))
            }
        }
    }
}

@Composable
private fun ParaAssistir(estado: EstadoDosFilmes, eventos: EventosDosFilmes) {
    val paraVer = estado.paraVer
    Cartao(stringResource(R.string.filmes_para_assistir), paraVer.size) {
        if (paraVer.isEmpty()) {
            Vazio(Icone.Filme, stringResource(R.string.filmes_vazio_titulo), texto = stringResource(R.string.filmes_vazio_texto))
        }
        paraVer.forEach { filme ->
            LinhaDoFilme(filme, eventos) {
                OutlinedButton({ eventos.alternarAssistido(filme) }) { Text(stringResource(R.string.filmes_ja_assistimos)) }
            }
        }
    }
}

@Composable
private fun JaAssistidos(estado: EstadoDosFilmes, eventos: EventosDosFilmes) {
    val assistidos = estado.assistidos
    Cartao(stringResource(R.string.filmes_ja_assistidos), assistidos.size) {
        if (assistidos.isEmpty()) {
            Vazio(Icone.Estrela, stringResource(R.string.filmes_nenhum_assistido), compacto = true)
        }
        assistidos.forEach { filme ->
            LinhaDoFilme(filme, eventos) {
                Text(stringResource(R.string.filmes_sua_nota), style = MaterialTheme.typography.labelMedium)
                Estrelas(estado.minhaNota(filme), { eventos.avaliar(filme, it) }, { stringResource(R.string.filmes_dar_nota, it) })
                estado.outrasNotas(filme).forEach { outra ->
                    Text(
                        stringResource(R.string.filmes_nota_de_outro, outra.nome ?: stringResource(R.string.filmes_ex_membro), outra.nota),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton({ eventos.alternarAssistido(filme) }) { Text(stringResource(R.string.filmes_voltar_para_a_fila)) }
            }
        }
    }
}

/** O pôster, o título, o ano e o [conteudo] (as ações), com o remover no canto. */
@Composable
private fun LinhaDoFilme(filme: Filme, eventos: EventosDosFilmes, conteudo: @Composable ColumnScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Poster(filme.poster, filme.titulo, 52.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(filme.titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                filme.ano?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            conteudo()
        }
        IconButton({ eventos.pedirParaTirar(filme) }) {
            Icon(painterResource(Icone.Lixeira.desenho), stringResource(R.string.filmes_remover_de, filme.titulo), tint = MaterialTheme.colorScheme.error)
        }
    }
}

/** O pôster do TMDB (proporção 2:3); sem pôster, o marcador de filme. */
@Composable
private fun Poster(url: String?, titulo: String, largura: Dp) {
    Box(
        Modifier.size(largura, largura * 1.5f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(Icone.Filme.desenho), null, Modifier.size(largura / 2.5f), tint = MaterialTheme.colorScheme.primary)
        if (!url.isNullOrBlank()) {
            AsyncImage(url, stringResource(R.string.filmes_poster_de, titulo), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun Cartao(titulo: String, quantos: Int? = null, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(titulo, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                quantos?.let {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
                        Text(it.toString(), Modifier.padding(horizontal = 10.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
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

/** "8,4": a nota do TMDB, de 0 a 10, com uma casa. */
private fun notaDoTmdb(nota: BigDecimal): String = String.format(Locale.forLanguageTag("pt-BR"), "%.1f", nota)

@Composable
private fun MensagemDeErro.texto(): String = texto(LocalContext.current)

/** Por que a lista não carregou, para o ícone e o texto do [EstadoDeErro]. */
internal fun falhaDe(erro: ErroDaApi): FalhaAoCarregar = when {
    erro is ErroDaApi.SemInternet -> FalhaAoCarregar.SemInternet
    erro is ErroDaApi.DoServidor && erro.status >= 500 -> FalhaAoCarregar.DoServidor
    else -> FalhaAoCarregar.Outra
}
