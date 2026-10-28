package br.com.piggu.app.moldura

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import br.com.piggu.app.R
import br.com.piggu.app.navegacao.Tela
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.designsystem.R as Icones
import kotlinx.coroutines.launch

/** O que a moldura mostra da pessoa e da conta. */
data class PerfilDaMoldura(
    val nome: String,
    val email: String,
    val ehTitular: Boolean,
    val premium: Boolean,
    /** Módulos de estilo de vida ligados; nulo liga todos. */
    val modulos: List<String>?,
    /** "1 BRL = US$ 0,1786", quando a cotação está ligada no Perfil. */
    val cotacao: String? = null,
    val cotacaoDesatualizada: Boolean = false,
    /** Falso: o Mais mostra o aviso de confirmar, com o reenviar. Ausente conta como confirmado. */
    val emailConfirmado: Boolean = true,
    /** Falso: o Mais oferece informar o documento (opcional), como o Perfil do site. */
    val cpfInformado: Boolean = true,
)

/** Como o menu aparece pela largura da janela (WindowSizeClass). */
enum class FormaDoMenu { Barra, Trilho, Gaveta }

@Composable
fun formaDoMenu(): FormaDoMenu {
    val classe = currentWindowAdaptiveInfo().windowSizeClass
    return when {
        classe.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> FormaDoMenu.Gaveta
        classe.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> FormaDoMenu.Trilho
        else -> FormaDoMenu.Barra
    }
}

/**
 * A moldura do app logado (layout/shell): o topo, o menu e o + de lançar.
 *
 * <p>No celular, a barra de abas embaixo (Painel, Gastos, Receitas, Relatórios e Mais). No
 * tablet ou no dobrável aberto, o trilho com as mesmas abas; na tela larga, a gaveta fixa com
 * os grupos do menu, como a coluna do site no computador.</p>
 *
 * @param abaAtual a aba marcada (a tela de cima da pilha, se for uma aba).
 * @param abrir troca de aba (nativa) ou abre a tela no site.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Moldura(
    perfil: PerfilDaMoldura,
    abaAtual: Tela?,
    demo: Boolean,
    abrir: (Tela) -> Unit,
    lancarGasto: () -> Unit,
    lancarReceita: () -> Unit,
    sair: () -> Unit,
    avisos: SnackbarHostState,
    conteudo: @Composable () -> Unit,
) {
    val forma = formaDoMenu()
    var opcoesAbertas by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()
    val emBreveBusca = stringResource(R.string.busca_em_breve)
    val emBreveAssistente = stringResource(R.string.assistente_em_breve)

    val principal: @Composable () -> Unit = {
        Scaffold(
            topBar = {
                Column {
                    if (demo) FaixaDaDemo()
                    Topo(
                        perfil = perfil,
                        irAoPainel = { abrir(Tela.Painel) },
                        buscar = { escopo.launch { avisos.showSnackbar(emBreveBusca) } },
                        perguntar = { escopo.launch { avisos.showSnackbar(emBreveAssistente) } },
                    )
                }
            },
            bottomBar = {
                if (forma == FormaDoMenu.Barra) BarraDeAbas(abaAtual, abrir)
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { opcoesAbertas = true }) {
                    Icon(painterResource(Icone.Mais.desenho), stringResource(R.string.lancar))
                }
            },
            snackbarHost = { SnackbarHost(avisos) },
            // As barras do sistema ficam com a moldura; o conteúdo recebe só a folga dela.
            contentWindowInsets = WindowInsets(0),
        ) { folga ->
            Box(Modifier.padding(folga).fillMaxSize()) { conteudo() }
        }
    }

    when (forma) {
        FormaDoMenu.Barra -> principal()
        FormaDoMenu.Trilho -> Row(Modifier.fillMaxSize()) {
            TrilhoDeAbas(abaAtual, abrir)
            Box(Modifier.weight(1f)) { principal() }
        }
        FormaDoMenu.Gaveta -> PermanentNavigationDrawer(
            drawerContent = { GavetaDoMenu(perfil, abaAtual, abrir, sair) },
            content = principal,
        )
    }

    if (opcoesAbertas) {
        // As opções do + (D-098 do iOS): gasto e receita, de qualquer aba.
        ModalBottomSheet(onDismissRequest = { opcoesAbertas = false }) {
            Text(
                stringResource(R.string.lancar_titulo),
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            OpcaoDoLancar(R.string.lancar_gasto, Icone.Lapis.desenho) { opcoesAbertas = false; lancarGasto() }
            OpcaoDoLancar(R.string.lancar_receita, Icone.Entrada.desenho) { opcoesAbertas = false; lancarReceita() }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OpcaoDoLancar(rotulo: Int, icone: Int, aoTocar: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(rotulo)) },
        leadingContent = { Icon(painterResource(icone), null) },
        // Os formulários chegam nativos na A4; até lá, abrem no site.
        trailingContent = { IconeAbreFora() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickableComPapel(Role.Button, aoTocar),
    )
}

@Composable
private fun FaixaDaDemo() {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            stringResource(R.string.faixa_demo),
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

/** Topo das abas (D-097 do iOS): a marca volta ao Painel, a cotação e, à direita, a busca e o assistente. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Topo(perfil: PerfilDaMoldura, irAoPainel: () -> Unit, buscar: () -> Unit, perguntar: () -> Unit) {
    TopAppBar(
        navigationIcon = {
            IconButton(irAoPainel) { MarcaDoApp(Modifier.size(36.dp), stringResource(R.string.voltar_ao_painel)) }
        },
        title = {
            perfil.cotacao?.let { cotacao ->
                val dica = stringResource(if (perfil.cotacaoDesatualizada) R.string.cotacao_desatualizada else R.string.cotacao_referencia)
                Column {
                    Text(cotacao, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(dica, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        },
        actions = {
            IconButton(buscar) { Icon(painterResource(Icone.Busca.desenho), stringResource(R.string.busca)) }
            IconButton(perguntar) { Icon(painterResource(Icone.Brilho.desenho), stringResource(R.string.assistente)) }
        },
    )
}

/** O ícone do app (as camadas do ícone adaptativo), recortado em círculo. */
@Composable
fun MarcaDoApp(modifier: Modifier = Modifier, descricao: String? = null) {
    Box(modifier.clip(CircleShape).semantics(mergeDescendants = true) {}) {
        Image(painterResource(R.mipmap.ic_launcher_background), descricao, Modifier.fillMaxSize().scale(1.5f))
        Image(painterResource(R.mipmap.ic_launcher_foreground), null, Modifier.fillMaxSize().scale(1.5f))
    }
}

@Composable
private fun BarraDeAbas(abaAtual: Tela?, abrir: (Tela) -> Unit) {
    NavigationBar {
        Abas.forEach { (tela, rotulo, icone) ->
            NavigationBarItem(
                selected = abaAtual == tela,
                onClick = { abrir(tela) },
                // O rótulo ao lado já é lido pelo TalkBack; descrever o ícone repetiria.
                icon = { Icon(painterResource(icone), null) },
                label = { Text(stringResource(rotulo), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun TrilhoDeAbas(abaAtual: Tela?, abrir: (Tela) -> Unit) {
    NavigationRail(Modifier.statusBarsPadding()) {
        Spacer(Modifier.height(8.dp))
        Abas.forEach { (tela, rotulo, icone) ->
            NavigationRailItem(
                selected = abaAtual == tela,
                onClick = { abrir(tela) },
                icon = { Icon(painterResource(icone), null) },
                label = { Text(stringResource(rotulo)) },
            )
        }
    }
}

/** As abas do celular, com os desenhos da barra (SIMBOLOS de shell.ts) e o Mais no fim. */
private val Abas: List<Triple<Tela, Int, Int>> = listOf(
    Triple(Tela.Painel, R.string.tela_painel, Icones.drawable.ic_painel),
    Triple(Tela.Gastos, R.string.tela_gastos, Icones.drawable.ic_gastos),
    Triple(Tela.Receitas, R.string.tela_receitas, Icones.drawable.ic_receitas),
    Triple(Tela.Relatorios, R.string.tela_relatorios, Icones.drawable.ic_relatorios),
    Triple(Tela.Mais, R.string.tela_mais, Icones.drawable.ic_mais),
)

/** Gaveta fixa da tela larga: o Perfil, os grupos do menu inteiros e o Sair, como a coluna do site. */
@Composable
private fun GavetaDoMenu(perfil: PerfilDaMoldura, abaAtual: Tela?, abrir: (Tela) -> Unit, sair: () -> Unit) {
    PermanentDrawerSheet(Modifier.width(300.dp)) {
        LazyColumn(Modifier.statusBarsPadding().padding(horizontal = 12.dp)) {
            item { CartaoDoPerfil(perfil) { abrir(Tela.Perfil) } }
            gruposVisiveis(perfil.ehTitular, perfil.modulos).forEach { (grupo, itens) ->
                item {
                    Text(
                        stringResource(grupo.titulo),
                        Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                itens.forEach { item ->
                    item {
                        NavigationDrawerItem(
                            label = { Text(stringResource(item.rotulo)) },
                            icon = { Icon(painterResource(item.icone.desenho), null) },
                            badge = { if (!item.tela.nativa) IconeAbreFora() },
                            selected = abaAtual == item.tela,
                            onClick = { abrir(item.tela) },
                        )
                    }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.sair)) },
                    icon = { Icon(painterResource(Icone.Sair.desenho), null) },
                    selected = false,
                    onClick = sair,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** O + flutuante fica por cima da lista; o fim dela ganha esta folga para nada ficar escondido. */
val FOLGA_DO_LANCAR = 88.dp
