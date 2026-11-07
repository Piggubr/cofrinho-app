package br.com.piggu.feature.contas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import br.com.piggu.core.model.LinhaDaConferencia
import br.com.piggu.core.ui.formatos.dataCurta
import br.com.piggu.core.ui.formatos.formatarMoeda
import br.com.piggu.core.ui.formatos.mesPorExtenso

/** "Conferir com a fatura do banco": a folha que lê o PDF e mostra o que já está lançado e o que falta. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConferirFatura(aberta: FaturaNaTela, c: ConferenciaNaTela, moeda: String, eventos: EventosDasContasECartoes) {
    val contexto = LocalContext.current
    val escolher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) eventos.lerPdf { contexto.contentResolver.openInputStream(uri) }
    }
    ModalBottomSheet(eventos.fecharConferencia, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            Modifier.fillMaxWidth().wrapContentWidth().widthIn(max = 720.dp),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.fatura_titulo, aberta.cartao.nome, mesPorExtenso(aberta.mes)),
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(R.string.conferir_titulo), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Text(stringResource(R.string.conferir_explicacao), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton({ escolher.launch(arrayOf("application/pdf")) }, Modifier.heightIn(min = 48.dp), enabled = !c.lendo && !c.adicionando) {
                    Text(stringResource(R.string.conferir_escolher))
                }
            }
            if (c.precisaSenha) item { Senha(c, eventos) }
            if (c.lendo) item { Carregando() }
            c.pedidoDeIa?.let { pedido ->
                item {
                    Text(pedido.texto(), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(eventos.autorizarIa, Modifier.heightIn(min = 48.dp), enabled = !c.lendo) { Text(stringResource(R.string.conferir_autorizar)) }
                        TextButton(eventos.recusarIa, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.conferir_cancelar)) }
                    }
                }
            }
            c.erro?.let { item { Aviso(it.texto(), erro = true) } }
            if (c.convite) {
                item {
                    Text(stringResource(R.string.conferir_convite), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(eventos.abrirPremium, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.conferir_conhecer_premium)) }
                }
            }
            c.leitura?.let { leitura ->
                item {
                    Text(
                        stringResource(R.string.conferir_contagem, leitura.contagem.jaLancadas, leitura.contagem.faltam),
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                    )
                    if (leitura.contagem.confira > 0) {
                        Text(
                            stringResource(R.string.conferir_para_conferir, leitura.contagem.confira),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    leitura.aviso?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            grupo(R.string.conferir_grupo_faltam, c.faltam, c, moeda, eventos)
            grupo(R.string.conferir_grupo_confira, c.confira, c, moeda, eventos)
            grupo(R.string.conferir_grupo_tarifas, c.tarifas, c, moeda, eventos)
            if (c.jaLancadas.isNotEmpty()) item { JaLancadas(c.jaLancadas, moeda) }
            if (c.marcadas.isNotEmpty()) {
                item {
                    Button(eventos.adicionarDaConferencia, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !c.adicionando) {
                        Text(stringResource(R.string.conferir_adicionar, c.marcadas.size))
                    }
                }
            }
            c.adicionadas?.let { n ->
                item {
                    Aviso(
                        if (c.jaEstavam > 0) stringResource(R.string.conferir_adicionadas_puladas, n, c.jaEstavam) else stringResource(R.string.conferir_adicionadas, n),
                        erro = false,
                    )
                }
            }
            c.venceEm(aberta.mes)?.let { outro ->
                item {
                    Text(
                        stringResource(R.string.conferir_vence_em_outro_mes, outro),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (c.ofereceQuitar(aberta.mes)) {
                item {
                    Button(eventos.quitarPelaConferencia, Modifier.heightIn(min = 48.dp), enabled = !c.quitando) { Text(stringResource(R.string.conferir_marcar_paga)) }
                }
            }
            if (c.paga) item { Aviso(stringResource(R.string.conferir_paga), erro = false) }
        }
    }
}

@Composable
private fun Carregando() {
    Row(
        Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(Modifier.padding(4.dp))
        Text(stringResource(R.string.conferir_lendo))
    }
}

@Composable
private fun Senha(c: ConferenciaNaTela, eventos: EventosDasContasECartoes) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            c.senha, eventos.mudarSenha, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.conferir_senha)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Button(eventos.tentarComSenha, Modifier.heightIn(min = 48.dp), enabled = !c.lendo && c.senha.isNotEmpty()) {
            Text(stringResource(R.string.conferir_tentar_de_novo))
        }
    }
}

@Composable
private fun Aviso(texto: String, erro: Boolean) {
    Surface(
        Modifier.fillMaxWidth(),
        color = if (erro) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            texto,
            Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Assertive },
            style = MaterialTheme.typography.bodyMedium,
            color = if (erro) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.grupo(
    titulo: Int, linhas: List<LinhaDaConferencia>, c: ConferenciaNaTela, moeda: String, eventos: EventosDasContasECartoes,
) {
    if (linhas.isEmpty()) return
    item { Text(stringResource(titulo, linhas.size), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall) }
    linhas.forEach { item(it.idExterno) { LinhaMarcavel(it, it.idExterno in c.marcadas, c.categorias, moeda, eventos) } }
}

@Composable
private fun LinhaMarcavel(l: LinhaDaConferencia, marcada: Boolean, categorias: List<String>, moeda: String, eventos: EventosDasContasECartoes) {
    val parcela = l.parcelaTotal?.let { stringResource(R.string.fatura_parcela, l.parcelaNumero ?: 0, it) }
    val parecido = l.gastoDescricao?.let { stringResource(R.string.conferir_parecido, it, dataCurta(l.gastoData)) }
    val rotulo = stringResource(R.string.conferir_marcar, l.descricao, formatarMoeda(l.valor, moeda))
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(marcada, role = Role.Checkbox, onValueChange = { eventos.alternarLinha(l.idExterno) })
                .semantics { contentDescription = rotulo },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(marcada, null)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(l.descricao, style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOfNotNull(dataCurta(l.data), parcela, parecido).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatarMoeda(l.valor, moeda), style = MaterialTheme.typography.bodyLarge)
        }
        Categoria(l, categorias, eventos)
    }
}

/** A categoria sugerida pela leitura; as opções são as mesmas da importação do extrato. */
@Composable
private fun Categoria(l: LinhaDaConferencia, categorias: List<String>, eventos: EventosDasContasECartoes) {
    var aberto by rememberSaveable(l.idExterno) { mutableStateOf(false) }
    val opcoes = if (l.categoria in categorias) categorias else listOf(l.categoria) + categorias
    val rotulo = stringResource(R.string.conferir_categoria, l.descricao, l.categoria)
    Column(Modifier.padding(start = 48.dp)) {
        TextButton({ aberto = true }, Modifier.heightIn(min = 48.dp).semantics { contentDescription = rotulo }) { Text(l.categoria) }
        DropdownMenu(aberto, { aberto = false }) {
            opcoes.forEach { categoria ->
                DropdownMenuItem(
                    text = { Text(categoria) },
                    onClick = { aberto = false; eventos.mudarCategoria(l.idExterno, categoria) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

/** Fechado de início: elas já estão no Piggu e nunca entram na importação. */
@Composable
private fun JaLancadas(linhas: List<LinhaDaConferencia>, moeda: String) {
    var aberto by rememberSaveable { mutableStateOf(false) }
    Column {
        TextButton({ aberto = !aberto }, Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.conferir_grupo_ja_lancadas, linhas.size))
        }
        if (aberto) {
            linhas.forEach { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.descricao, style = MaterialTheme.typography.bodyLarge)
                        Text(dataCurta(l.data), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatarMoeda(l.valor, moeda), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
