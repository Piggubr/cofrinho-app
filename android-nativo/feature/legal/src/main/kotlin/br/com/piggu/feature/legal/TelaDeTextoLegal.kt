package br.com.piggu.feature.legal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.designsystem.R as Icones
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.R as Comum
import br.com.piggu.core.ui.Vazio
import br.com.piggu.core.ui.lembrarAbrirNoSite
import kotlinx.coroutines.launch

// O item 0 da lista é a abertura com o sumário; a seção i é o item i + 1.
private const val PRIMEIRA_SECAO = 1

/**
 * Termos de Uso ou Aviso de Privacidade, com o texto do site guardado no app: abre sem login e
 * sem internet. [abrirLinkInterno] recebe a rota do site de um link do texto (como
 * "/privacidade"); sem ele, a rota abre no site.
 */
@Composable
fun TelaDeTextoLegal(
    documento: DocumentoLegal,
    voltar: () -> Unit,
    modifier: Modifier = Modifier,
    abrirLinkInterno: (caminho: String) -> Unit = lembrarAbrirNoSite(),
) {
    val assets = LocalContext.current.applicationContext.assets
    val viewModel = viewModel(key = documento.name) {
        TextoLegalViewModel(documento, { arquivo -> assets.open(arquivo).bufferedReader().use { it.readText() } })
    }
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    TelaDeTextoLegal(estado, voltar, abrirLinkInterno, modifier)
}

/** A tela desenhando um [EstadoDoTextoLegal]; o sumário rola até a seção tocada. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDeTextoLegal(
    estado: EstadoDoTextoLegal,
    voltar: () -> Unit,
    abrirLinkInterno: (caminho: String) -> Unit,
    modifier: Modifier = Modifier,
    lista: LazyListState = rememberLazyListState(),
) {
    val rolar = rememberCoroutineScope()
    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(estado.documento.titulo), Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(voltar) { Icon(painterResource(Icones.drawable.ic_esquerda), stringResource(Comum.string.voltar)) }
                },
            )
        },
    ) { margens ->
        val texto = estado.texto
        when {
            estado.carregando -> Esqueleto(Modifier.padding(margens))
            texto == null -> {
                val abrirNoSite = lembrarAbrirNoSite()
                Vazio(
                    Icone.Nota,
                    stringResource(R.string.legal_nao_abriu),
                    Modifier.padding(margens),
                    stringResource(R.string.legal_leia_no_site),
                    acao = stringResource(Comum.string.abrir_no_site) to { abrirNoSite(estado.documento.caminho) },
                )
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                lista,
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = margens.calculateTopPadding() + 8.dp,
                    bottom = margens.calculateBottomPadding() + 24.dp,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Coluna {
                        Text(
                            texto.titulo,
                            Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        texto.abertura.forEach { DesenharBloco(it, abrirLinkInterno) }
                        Sumario(texto.secoes) { indice -> rolar.launch { lista.animateScrollToItem(PRIMEIRA_SECAO + indice) } }
                    }
                }
                itemsIndexed(texto.secoes) { _, secao ->
                    Coluna(Modifier.padding(top = 20.dp)) {
                        Text(secao.titulo, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                        secao.blocos.forEach { DesenharBloco(it, abrirLinkInterno) }
                    }
                }
            }
        }
    }
}

/** Largura de leitura, como o .documento do site (760px). */
@Composable
private fun Coluna(modifier: Modifier = Modifier, conteudo: @Composable () -> Unit) {
    Column(modifier.widthIn(max = 680.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) { conteudo() }
}

@Composable
private fun Sumario(secoes: List<Secao>, irPara: (Int) -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(top = 8.dp), RoundedCornerShape(16.dp), MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.legal_nesta_pagina),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
                style = MaterialTheme.typography.titleSmall,
            )
            secoes.forEachIndexed { indice, secao ->
                Text(
                    secao.titulo,
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button) { irPara(indice) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun DesenharBloco(bloco: Bloco, abrirLinkInterno: (String) -> Unit) {
    when (bloco) {
        is Bloco.Paragrafo -> TextoRico(bloco.trechos, abrirLinkInterno)
        is Bloco.Item -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("•", Modifier.clearAndSetSemantics {}, color = MaterialTheme.colorScheme.primary)
            TextoRico(bloco.trechos, abrirLinkInterno)
        }
        // A tabela do site vira um cartão por linha: a primeira coluna em destaque e as outras
        // com o nome da coluna, como no iOS.
        is Bloco.LinhaDaTabela -> Surface(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            RoundedCornerShape(12.dp),
            MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                bloco.colunas.firstOrNull()?.let {
                    TextoRico(it, abrirLinkInterno, MaterialTheme.typography.titleSmall)
                }
                bloco.colunas.drop(1).forEachIndexed { indice, coluna ->
                    val nome = bloco.cabecalho.getOrNull(indice + 1)
                    TextoRico(
                        listOfNotNull(nome?.let { Trecho("$it: ", negrito = true) }) + coluna,
                        abrirLinkInterno,
                        MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            }
        }
    }
}

/** Negrito e links clicáveis: rota do site chama [abrirLinkInterno]; mailto e https abrem fora. */
@Composable
private fun TextoRico(trechos: List<Trecho>, abrirLinkInterno: (String) -> Unit, estilo: TextStyle = MaterialTheme.typography.bodyLarge) {
    val cor = MaterialTheme.colorScheme.primary
    val navegador = LocalUriHandler.current
    val texto = remember(trechos, cor, abrirLinkInterno, navegador) {
        val aparencia = TextLinkStyles(SpanStyle(color = cor, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline))
        buildAnnotatedString {
            trechos.forEach { trecho ->
                val destino = trecho.link
                when {
                    destino != null && trecho.linkInterno ->
                        withLink(LinkAnnotation.Clickable(destino, aparencia) { abrirLinkInterno(destino) }) { append(trecho.texto) }
                    // Sem app de e-mail, openUri lança: o toque só não faz nada.
                    destino != null ->
                        withLink(LinkAnnotation.Url(destino, aparencia) { runCatching { navegador.openUri(destino) } }) { append(trecho.texto) }
                    trecho.negrito -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(trecho.texto) }
                    else -> append(trecho.texto)
                }
            }
        }
    }
    Text(texto, style = estilo)
}
