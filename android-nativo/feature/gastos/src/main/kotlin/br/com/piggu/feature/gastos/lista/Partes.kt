package br.com.piggu.feature.gastos.lista

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.model.Gasto
import br.com.piggu.core.model.MembroDaFamilia
import br.com.piggu.core.ui.CampoDeCategoria
import br.com.piggu.core.ui.CampoDeValor
import br.com.piggu.core.ui.SeletorDeMes
import br.com.piggu.core.ui.formatos.dataCurta
import br.com.piggu.core.ui.formatos.formatarMoeda
import br.com.piggu.feature.gastos.R
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val DIA_POR_EXTENSO = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT_BR)

/** "2026-10-28" vira "Quarta-feira, 28 de outubro". */
internal fun diaPorExtenso(dia: String): String = try {
    DIA_POR_EXTENSO.format(LocalDate.parse(dia)).replaceFirstChar { it.titlecase(PT_BR) }
} catch (_: DateTimeParseException) {
    dia
}

/** O nome de quem está na família pelo id (pessoas-da-familia.ts); quem saiu vira "Ex-membro". */
@Composable
private fun nomeNaFamilia(membros: List<MembroDaFamilia>, id: String?): String =
    if (id == SISTEMA) "Piggu" else membros.firstOrNull { it.id == id }?.nome ?: stringResource(R.string.gastos_ex_membro)

/** O mês, a busca e os filtros. */
@Composable
internal fun Topo(estado: EstadoDosGastos, acoes: AcoesDosGastos) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SeletorDeMes(estado.mes, estado.mesAtual, acoes::mudarMes, Modifier.align(Alignment.CenterHorizontally))
        OutlinedTextField(
            estado.busca,
            acoes::mudarBusca,
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.gastos_buscar)) },
            leadingIcon = { Icon(painterResource(Icone.Busca.desenho), null) },
            trailingIcon = if (estado.busca.isEmpty()) null else {
                {
                    IconButton({ acoes.mudarBusca("") }) {
                        Icon(painterResource(Icone.Fechar.desenho), stringResource(R.string.gastos_limpar_busca))
                    }
                }
            },
            singleLine = true,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipComMenu(
                rotulo = estado.filtroDeCategoria ?: stringResource(R.string.gastos_categoria),
                descricao = stringResource(R.string.gastos_filtrar_categoria),
                ativo = estado.filtroDeCategoria != null,
                opcoes = listOf<Pair<String, () -> Unit>>(stringResource(R.string.gastos_todas_as_categorias) to { acoes.filtrarCategoria(null) }) +
                    estado.categorias.map { c -> c to { acoes.filtrarCategoria(c) } },
            )
            ChipComMenu(
                rotulo = stringResource(if (estado.filtroDePagamento == FiltroDePagamento.Todas) R.string.gastos_pagamento else estado.filtroDePagamento.nome),
                descricao = stringResource(R.string.gastos_filtrar_pagamento),
                ativo = estado.filtroDePagamento != FiltroDePagamento.Todas,
                opcoes = FiltroDePagamento.entries.map { f -> stringResource(f.nome) to { acoes.filtrarPagamento(f) } },
            )
        }
    }
}

@Composable
private fun ChipComMenu(rotulo: String, descricao: String, ativo: Boolean, opcoes: List<Pair<String, () -> Unit>>) {
    var aberto by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = ativo,
            onClick = { aberto = true },
            label = { Text(rotulo, maxLines = 1) },
            modifier = Modifier.semantics { contentDescription = "$descricao: $rotulo" },
        )
        DropdownMenu(aberto, { aberto = false }) {
            opcoes.forEach { (texto, escolher) -> DropdownMenuItem({ Text(texto) }, { escolher(); aberto = false }) }
        }
    }
}

@Composable
internal fun Total(estado: EstadoDosGastos, acoes: AcoesDosGastos) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(if (estado.filtrando) R.string.gastos_total_filtrado else R.string.gastos_total_do_mes),
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(formatarMoeda(estado.total, estado.moeda), style = MaterialTheme.typography.titleMedium)
    }
    if (estado.filtrando) {
        TextButton(acoes::limparFiltros, Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.gastos_limpar_filtros)) }
    }
}

@Composable
internal fun AvisoDeRecusados(quantos: Int, entendi: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), MaterialTheme.shapes.medium, MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(R.plurals.gastos_recusados, quantos, quantos),
                Modifier.weight(1f).padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(entendi) { Text(stringResource(R.string.gastos_entendi)) }
        }
    }
}

@Composable
internal fun SugestaoDeRegra(sugestao: RegraSugerida, acoes: AcoesDosGastos) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), MaterialTheme.shapes.medium, MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.regras_sugestao, sugestao.categoria, sugestao.termo), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ acoes.criarRegra(sugestao.termo, sugestao.categoria) }) { Text(stringResource(R.string.regras_criar)) }
                TextButton(acoes::dispensarSugestao) { Text(stringResource(R.string.regras_agora_nao)) }
            }
        }
    }
}

/** Uma linha: toque edita; deslizar para a esquerda apaga (com a ação no TalkBack também). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LinhaDoGasto(linha: LinhaDeGasto, estado: EstadoDosGastos, acoes: AcoesDosGastos) {
    val gasto = linha.gasto
    val podeApagar = !linha.aguardando && estado.podeApagar(gasto)
    val apagar = stringResource(R.string.gastos_apagar)
    val editar = stringResource(R.string.gastos_editar)
    val conteudo = @Composable {
        ListItem(
            headlineContent = { Text(gasto.item) },
            supportingContent = { Text(detalhe(linha, estado)) },
            trailingContent = { Text(formatarMoeda(gasto.valor, estado.moeda), style = MaterialTheme.typography.bodyLarge) },
            colors = ListItemDefaults.colors(
                containerColor = if (gasto.id == estado.destacado) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            ),
            modifier = if (linha.aguardando) Modifier.semantics(mergeDescendants = true) {} else Modifier
                .clickable(onClickLabel = editar, role = Role.Button) { acoes.abrirEdicao(gasto) }
                .semantics {
                    if (podeApagar) customActions = listOf(CustomAccessibilityAction(apagar) { acoes.excluir(gasto); true })
                },
        )
    }
    if (!podeApagar) {
        conteudo()
        return
    }
    val deslize = rememberSwipeToDismissBoxState()
    val escopo = rememberCoroutineScope()
    SwipeToDismissBox(
        deslize,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(painterResource(Icone.Lixeira.desenho), null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
        enableDismissFromStartToEnd = false,
        onDismiss = {
            acoes.excluir(gasto)
            // A parcela pergunta antes: se cancelar, a linha volta ao lugar.
            escopo.launch { deslize.reset() }
        },
    ) { conteudo() }
}

/** O detalhe do site: categoria, parcela, estabelecimento, origem, moeda da compra, compartilhado e de quem é. */
@Composable
private fun detalhe(linha: LinhaDeGasto, estado: EstadoDosGastos): String {
    val g = linha.gasto
    val partes = buildList {
        g.categoria.takeIf { it.isNotEmpty() }?.let(::add)
        val parcela = g.parcela
        val parcelas = g.parcelas
        if (parcela != null && parcelas != null) add(stringResource(R.string.gastos_parcela, parcela, parcelas))
        g.estabelecimento?.takeIf { it.isNotBlank() }?.let(::add)
        g.origem?.takeIf { it.isNotBlank() && it != "Manual" }?.let(::add)
        g.moedaOriginal?.let { moeda -> g.valorOriginal?.let { add(formatarMoeda(it, moeda)) } }
        if (g.compartilhado == true) add(stringResource(R.string.gastos_compartilhado))
        if (!estado.ehMeu(g) && estado.comCompanhia && !linha.aguardando) add(stringResource(R.string.gastos_de, nomeNaFamilia(estado.membros, g.usuario)))
        if (linha.aguardando) add(stringResource(R.string.gastos_aguardando_envio))
    }
    return partes.joinToString(" · ")
}

@Composable
private fun Cartao(titulo: String, conteudo: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            conteudo()
        }
    }
}

@Composable
internal fun DivisaoDoMes(estado: EstadoDosGastos) {
    val moeda = estado.moeda
    Cartao(stringResource(R.string.divisao_titulo)) {
        estado.transferencias.forEach { t ->
            Text(
                stringResource(
                    R.string.divisao_paga,
                    nomeNaFamilia(estado.membros, t.de),
                    formatarMoeda(t.valor, moeda),
                    nomeNaFamilia(estado.membros, t.para),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        estado.acerto.forEach { pessoa ->
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(nomeNaFamilia(estado.membros, pessoa.pessoa))
                    Text(
                        stringResource(R.string.divisao_pagou, formatarMoeda(pessoa.pagou, moeda), formatarMoeda(pessoa.parte, moeda)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    when (pessoa.saldo.signum()) {
                        1 -> stringResource(R.string.divisao_recebe, formatarMoeda(pessoa.saldo, moeda))
                        -1 -> stringResource(R.string.divisao_deve, formatarMoeda(pessoa.saldo.negate(), moeda))
                        else -> stringResource(R.string.divisao_quite)
                    },
                )
            }
        }
    }
}

@Composable
internal fun ImportarEExportar(estado: EstadoDosGastos, acoes: AcoesDosGastos, escolherExtrato: () -> Unit) {
    Cartao(stringResource(R.string.importar_titulo)) {
        Text(stringResource(R.string.importar_explicacao), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(escolherExtrato, enabled = !estado.ocupado) { Text(stringResource(R.string.importar_escolher)) }
            OutlinedButton(acoes::exportar) { Text(stringResource(R.string.importar_exportar)) }
        }
        val previa = estado.previa ?: return@Cartao
        val marcadas = previa.filter { it.marcada }
        Text(
            pluralStringResource(
                R.plurals.importar_marcados,
                previa.size,
                marcadas.size,
                previa.size,
                formatarMoeda(marcadas.fold(BigDecimal.ZERO) { s, l -> s + l.linha.valor }, estado.moeda),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        if (estado.contas.isNotEmpty()) {
            val nenhuma = stringResource(R.string.importar_nao_informar)
            Seletor(
                rotulo = stringResource(R.string.importar_conta),
                valor = estado.contas.firstOrNull { it.id == estado.contaDoExtrato }?.nome ?: nenhuma,
                opcoes = listOf(nenhuma to "") + estado.contas.map { it.nome to it.id },
                escolher = acoes::mudarContaDoExtrato,
            )
        }
        previa.forEachIndexed { i, l ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(l.marcada, role = Role.Checkbox) { acoes.alternarLinha(i) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(l.marcada, null)
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.linha.descricao)
                    val detalhe = dataCurta(l.linha.data) +
                        if (l.linha.jaImportada) " · " + stringResource(R.string.importar_ja_importado) else ""
                    Text(detalhe, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatarMoeda(l.linha.valor, estado.moeda))
            }
            Seletor(
                rotulo = stringResource(R.string.importar_categoria_de, l.linha.descricao),
                valor = l.linha.categoria,
                opcoes = estado.categorias.map { it to it },
                escolher = { acoes.mudarCategoriaDaLinha(i, it) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(acoes::importarMarcados, enabled = !estado.ocupado && marcadas.isNotEmpty()) {
                Text(stringResource(R.string.importar_importar_marcados))
            }
            TextButton(acoes::cancelarPrevia) { Text(stringResource(R.string.gastos_cancelar)) }
        }
    }
}

/** Um botão com o escolhido que abre a lista; o leitor de tela lê o rótulo junto. */
@Composable
private fun Seletor(rotulo: String, valor: String, opcoes: List<Pair<String, String>>, escolher: (String) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ aberto = true }, Modifier.semantics { contentDescription = "$rotulo: $valor" }) { Text(valor, maxLines = 1) }
        DropdownMenu(aberto, { aberto = false }) {
            opcoes.forEach { (texto, id) -> DropdownMenuItem({ Text(texto) }, { escolher(id); aberto = false }) }
        }
    }
}

@Composable
internal fun RegrasDeCategoria(estado: EstadoDosGastos, acoes: AcoesDosGastos, criarCategoria: suspend (String) -> Result<List<String>>) {
    Cartao(stringResource(R.string.regras_titulo)) {
        Text(stringResource(R.string.regras_explicacao), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        estado.regras.forEach { regra ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.regras_regra, regra.termo, regra.categoria), Modifier.weight(1f))
                IconButton({ acoes.excluirRegra(regra) }) {
                    Icon(painterResource(Icone.Lixeira.desenho), stringResource(R.string.regras_apagar, regra.termo))
                }
            }
        }
        if (estado.regras.isEmpty()) {
            Text(stringResource(R.string.regras_vazio), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            estado.termoDaRegra,
            acoes::mudarTermoDaRegra,
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.regras_quando_tiver)) },
            singleLine = true,
        )
        CampoDeCategoria(
            estado.categorias,
            estado.categoriaDaRegra,
            acoes::mudarCategoriaDaRegra,
            criarCategoria,
            acoes::categoriasCriadas,
            Modifier.fillMaxWidth(),
            automatica = false,
        )
        Button({ acoes.criarRegra(estado.termoDaRegra, estado.categoriaDaRegra) }) { Text(stringResource(R.string.regras_salvar)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditarGasto(
    edicao: EdicaoDoGasto,
    categorias: List<String>,
    moeda: String?,
    acoes: AcoesDosGastos,
    criarCategoria: suspend (String) -> Result<List<String>>,
) {
    ModalBottomSheet(acoes::fecharEdicao, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.gastos_editar_titulo), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                edicao.item,
                acoes::mudarItem,
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.gastos_item)) },
                singleLine = true,
            )
            CampoDeCategoria(categorias, edicao.categoria, acoes::mudarCategoria, criarCategoria, acoes::categoriasCriadas, Modifier.fillMaxWidth(), automatica = false)
            CampoDeValor(edicao.valor, acoes::mudarValor, moeda ?: "BRL", Modifier.fillMaxWidth())
            if (edicao.podeCompartilhar) {
                Row(
                    Modifier.fillMaxWidth().toggleable(edicao.compartilhado, role = Role.Switch, onValueChange = acoes::mudarCompartilhado),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.gastos_compartilhado).replaceFirstChar { it.titlecase() }, Modifier.weight(1f))
                    Switch(edicao.compartilhado, null)
                }
            }
            edicao.erro?.let { erro ->
                Text(erro.texto(LocalContext.current), color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(acoes::fecharEdicao) { Text(stringResource(R.string.gastos_cancelar)) }
                Button(acoes::salvarEdicao, enabled = !edicao.salvando) { Text(stringResource(R.string.gastos_salvar)) }
            }
        }
    }
}

/** A parcela com próximas: só esta, ou esta e as seguintes (como o site). */
@Composable
internal fun ConfirmarExclusao(gasto: Gasto, acoes: AcoesDosGastos) {
    val seguintes = gasto.seguintes()
    AlertDialog(
        onDismissRequest = acoes::cancelarExclusao,
        title = { Text(stringResource(R.string.gastos_apagar_pergunta, gasto.item)) },
        text = { Text(stringResource(R.string.gastos_parcela_de, gasto.parcela ?: 0, gasto.parcelas ?: 0)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton({ acoes.apagar(gasto, eAsProximas = true) }) {
                    Text(pluralStringResource(R.plurals.gastos_apagar_e_as_proximas, seguintes, seguintes))
                }
                TextButton({ acoes.apagar(gasto, eAsProximas = false) }) { Text(stringResource(R.string.gastos_apagar_so_esta)) }
                TextButton(acoes::cancelarExclusao) { Text(stringResource(R.string.gastos_cancelar)) }
            }
        },
    )
}
