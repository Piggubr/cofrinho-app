package br.com.piggu.feature.assistente.assistente

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.ui.EstadoDeErro
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.FalhaAoCarregar
import br.com.piggu.core.ui.HumorDoPorquinho
import br.com.piggu.core.ui.Porquinho
import br.com.piggu.core.ui.Vazio
import br.com.piggu.feature.assistente.R

private val SUGESTOES = listOf(
    R.string.assistente_sugestao_1,
    R.string.assistente_sugestao_2,
    R.string.assistente_sugestao_3,
    R.string.assistente_sugestao_4,
)

/**
 * O assistente de IA do topo, numa folha (D-097 do iOS). [abrirCaminho] recebe um caminho do app
 * ("/plano", "/relatorios", "/privacidade"); a folha fecha antes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolhaDoAssistente(aberta: Boolean, fechar: () -> Unit, abrirCaminho: (String) -> Unit) {
    val viewModel: AssistenteViewModel = hiltViewModel()
    LaunchedEffect(aberta) { if (aberta) viewModel.abrir() }
    if (!aberta) return
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = fechar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ConteudoDoAssistente(
            estado = estado,
            fechar = fechar,
            ir = { caminho ->
                fechar()
                abrirCaminho(caminho)
            },
            recomecar = viewModel::recomecar,
            autorizar = viewModel::autorizar,
            mudarTexto = viewModel::mudarTexto,
            perguntar = viewModel::perguntar,
            tentarDeNovo = viewModel::tentarDeNovo,
        )
    }
}

/** O conteúdo da folha, sem estado: desenha o [estado] e chama os eventos (capturas e testes). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConteudoDoAssistente(
    estado: EstadoDoAssistente,
    fechar: () -> Unit,
    ir: (String) -> Unit,
    recomecar: () -> Unit,
    autorizar: () -> Unit,
    mudarTexto: (String) -> Unit,
    perguntar: (String) -> Unit,
    tentarDeNovo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    Column(modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Porquinho(HumorDoPorquinho.Feliz, Modifier.clearAndSetSemantics { }, largura = 40.dp)
            Text(
                stringResource(R.string.assistente_titulo),
                Modifier.weight(1f).padding(start = 8.dp).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            if (estado.falas.isNotEmpty()) {
                val dica = stringResource(R.string.assistente_nova_conversa_dica)
                TextButton(recomecar, Modifier.semantics { contentDescription = dica }) {
                    Text(stringResource(R.string.assistente_nova_conversa))
                }
            }
            IconButton(fechar) {
                Icon(painterResource(Icone.Fechar.desenho), stringResource(R.string.assistente_fechar))
            }
        }
        when {
            !estado.premium -> Explicacao {
                Text(stringResource(R.string.assistente_convite))
                Button({ ir("/plano") }) { Text(stringResource(R.string.assistente_conhecer_premium)) }
            }
            estado.carregando -> Esqueleto(linhas = 3)
            estado.situacao == null && estado.falhaAoCarregar != null ->
                EstadoDeErro(estado.falhaAoCarregar, tentarDeNovo, mensagem = estado.erro?.texto(contexto))
            estado.desligado -> Vazio(Icone.Brilho, stringResource(R.string.assistente_desligado_titulo), texto = stringResource(R.string.assistente_desligado))
            estado.precisaAutorizar -> Explicacao {
                Text(stringResource(R.string.assistente_consentimento))
                Text(
                    stringResource(R.string.assistente_consentimento_detalhe),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton({ ir("/privacidade") }) { Text(stringResource(R.string.assistente_aviso_de_privacidade)) }
                Button(autorizar) { Text(stringResource(R.string.assistente_autorizo)) }
            }
            else -> Conversa(estado, ir, mudarTexto, perguntar)
        }
    }
}

@Composable
private fun Explicacao(conteudo: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = conteudo)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Conversa(estado: EstadoDoAssistente, ir: (String) -> Unit, mudarTexto: (String) -> Unit, perguntar: (String) -> Unit) {
    val lista = rememberLazyListState()
    val total = estado.falas.size + if (estado.pensando) 1 else 0
    LaunchedEffect(total) { if (total > 0) lista.animateScrollToItem(total - 1) }
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 420.dp).semantics { liveRegion = LiveRegionMode.Polite },
        state = lista,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (estado.falas.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.assistente_vazio),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SUGESTOES.forEach { sugestao ->
                            val texto = stringResource(sugestao)
                            OutlinedButton({ perguntar(texto) }) { Text(texto) }
                        }
                    }
                }
            }
        }
        itemsIndexed(estado.falas) { _, fala -> Vez(fala, ir) }
        if (estado.pensando) {
            item {
                Text(
                    stringResource(R.string.assistente_pensando),
                    Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    estado.erro?.let { erro ->
        Text(
            erro.texto(LocalContext.current),
            Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            estado.texto,
            mudarTexto,
            Modifier.weight(1f),
            label = { Text(stringResource(R.string.assistente_sua_pergunta)) },
            placeholder = { Text(stringResource(R.string.assistente_exemplo)) },
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { perguntar(estado.texto) }),
        )
        FilledIconButton({ perguntar(estado.texto) }, Modifier.size(48.dp), enabled = estado.podeEnviar) {
            Icon(painterResource(Icone.Enviar.desenho), stringResource(R.string.assistente_enviar))
        }
    }
    estado.situacao?.let { situacao ->
        Text(
            pluralStringResource(R.plurals.assistente_rodape, situacao.limite, situacao.restantes, situacao.limite),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Vez(fala: FalaGuardada, ir: (String) -> Unit) {
    val descricao = stringResource(
        if (fala.pessoa) R.string.assistente_fala_da_pessoa else R.string.assistente_fala_do_assistente,
        fala.texto,
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = if (fala.pessoa) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .padding(start = if (fala.pessoa) 40.dp else 0.dp, end = if (fala.pessoa) 0.dp else 40.dp)
                .background(
                    if (fala.pessoa) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(16.dp),
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                fala.texto,
                Modifier.semantics { contentDescription = descricao },
                color = if (fala.pessoa) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            fala.nomeDaTela()?.let { nome ->
                OutlinedButton({ ir("/${fala.tela}") }) {
                    Text(stringResource(R.string.assistente_abrir_tela, stringResource(nome)))
                }
            }
        }
    }
}
