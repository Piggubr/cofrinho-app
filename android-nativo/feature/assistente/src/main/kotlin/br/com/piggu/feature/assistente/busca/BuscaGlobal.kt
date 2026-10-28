package br.com.piggu.feature.assistente.busca

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.feature.assistente.R

private val NOME_DO_GRUPO = mapOf(
    GrupoDaBusca.Telas to R.string.busca_grupo_telas,
    GrupoDaBusca.Gastos to R.string.busca_grupo_gastos,
    GrupoDaBusca.Lugares to R.string.busca_grupo_lugares,
    GrupoDaBusca.Filmes to R.string.busca_grupo_filmes,
    GrupoDaBusca.Compras to R.string.busca_grupo_compras,
)

/**
 * A busca global do topo (lupa do site), por cima de tudo. [telas] são as do menu, como
 * (rótulo, caminho). Tocar num resultado fecha a busca e abre a tela, ou o gasto no mês dele.
 */
@Composable
fun BuscaGlobal(
    telas: List<Pair<String, String>>,
    aberta: Boolean,
    fechar: () -> Unit,
    abrirCaminho: (String) -> Unit,
    abrirGasto: (id: String, mes: String) -> Unit,
) {
    val viewModel: BuscaViewModel = hiltViewModel()
    LaunchedEffect(telas) { viewModel.mudarTelas(telas) }
    LaunchedEffect(aberta) { if (aberta) viewModel.abrir() else viewModel.fechar() }
    if (!aberta) return
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    Dialog(fechar, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ConteudoDaBusca(
            estado = estado,
            digitar = viewModel::digitar,
            fechar = fechar,
            escolher = { destino ->
                fechar()
                when (destino) {
                    is DestinoDaBusca.Caminho -> abrirCaminho(destino.caminho)
                    is DestinoDaBusca.Gasto -> abrirGasto(destino.id, destino.mes)
                }
            },
        )
    }
}

/** A busca aberta, sem estado: desenha o [estado] e chama os eventos (capturas e testes). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConteudoDaBusca(
    estado: EstadoDaBusca,
    digitar: (String) -> Unit,
    fechar: () -> Unit,
    escolher: (DestinoDaBusca) -> Unit,
    modifier: Modifier = Modifier,
) {
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }
    // Uma superfície de tela cheia com o campo no alto: o SearchBar expandido dentro do Dialog
    // ficava com o campo no meio da tela.
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            SearchBarDefaults.InputField(
                query = estado.termo,
                onQueryChange = digitar,
                onSearch = { estado.resultados.firstOrNull()?.let { escolher(it.destino) } },
                expanded = true,
                onExpandedChange = { if (!it) fechar() },
                modifier = Modifier.fillMaxWidth().focusRequester(foco),
                placeholder = { Text(stringResource(R.string.busca_placeholder)) },
                leadingIcon = {
                    IconButton(fechar) {
                        Icon(painterResource(Icone.Esquerda.desenho), stringResource(R.string.busca_fechar))
                    }
                },
            )
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(estado.resultados) { indice, resultado ->
                    if (indice == 0 || estado.resultados[indice - 1].grupo != resultado.grupo) {
                        Text(
                            stringResource(NOME_DO_GRUPO.getValue(resultado.grupo)),
                            Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ListItem(
                        headlineContent = { Text(resultado.titulo) },
                        supportingContent = resultado.detalhe.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
                        leadingContent = { Icon(painterResource(resultado.icone.desenho), null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(role = Role.Button) { escolher(resultado.destino) },
                    )
                }
                if (estado.nadaEncontrado) {
                    item {
                        Text(
                            stringResource(R.string.busca_nada_encontrado, estado.termo.trim()),
                            Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
