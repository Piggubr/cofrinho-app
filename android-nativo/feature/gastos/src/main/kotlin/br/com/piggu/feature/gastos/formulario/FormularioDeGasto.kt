package br.com.piggu.feature.gastos.formulario

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.CampoDeCategoria
import br.com.piggu.core.ui.CampoDeValor
import br.com.piggu.core.ui.formatos.dataCurta
import br.com.piggu.core.ui.formatos.formatarMoeda
import br.com.piggu.core.ui.formatos.mesPorExtenso
import br.com.piggu.core.ui.lembrarAbrirNoSite
import br.com.piggu.feature.gastos.R
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * O formulário de gasto, o mesmo do + e da aba de Gastos. Rola sozinho: não ponha dentro de outra
 * rolagem vertical.
 *
 * @param aoLancar o gasto foi lançado (ou guardado sem internet), com a mensagem para o aviso;
 *   quem abriu o formulário decide se fecha.
 * @param valorInicial o valor do atalho ou do link (piggu://gasto?valor=12.50).
 */
@Composable
fun FormularioDeGasto(
    aoLancar: (mensagem: String) -> Unit,
    valorInicial: BigDecimal? = null,
    modifier: Modifier = Modifier,
    viewModel: FormularioDeGastoViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val recursos = LocalResources.current
    LaunchedEffect(valorInicial) { viewModel.preencherValor(valorInicial) }
    LaunchedEffect(estado.lancado) {
        estado.lancado?.let { aviso ->
            aoLancar(recursos.getString(aviso.texto, *aviso.argumentos.toTypedArray()))
            viewModel.avisoEntregue()
        }
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (estado.semInternet) Aviso(stringResource(R.string.formulario_sem_internet), erro = false)
        estado.erro?.let { Aviso(it.texto(contexto), erro = true) }

        estado.itens.forEachIndexed { indice, linha ->
            key(linha.chave) { LinhaDoItem(estado, linha, indice, viewModel) }
        }
        if (estado.itens.size < 100) {
            TextButton(viewModel::adicionarItem) {
                Icon(painterResource(Icone.Mais.desenho), null)
                Text(stringResource(R.string.formulario_adicionar_item))
            }
        }

        ComoPagou(estado, viewModel)
        if (estado.forma == FormaDePagamento.CARTAO) ComoFoiACompra(estado, viewModel)
        MaisOpcoes(estado, viewModel)

        Button(viewModel::lancar, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !estado.salvando) {
            Text(stringResource(if (estado.salvando) R.string.formulario_guardando else R.string.formulario_lancar))
        }
    }
}

@Composable
private fun Aviso(texto: String, erro: Boolean) {
    Surface(
        color = if (erro) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(texto, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LinhaDoItem(estado: EstadoDoFormulario, linha: ItemDoFormulario, indice: Int, viewModel: FormularioDeGastoViewModel) {
    val contexto = LocalContext.current
    val comErro = estado.itemComErro == linha.chave
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (estado.itens.size > 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.formulario_item_numero, indice + 1), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                IconButton({ viewModel.removerItem(linha.chave) }) {
                    Icon(painterResource(Icone.Lixeira.desenho), stringResource(R.string.formulario_remover_item, indice + 1))
                }
            }
        }
        CampoDeValor(
            linha.valor,
            { viewModel.mudarValor(linha.chave, it) },
            estado.moedaDaCompra,
            Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) viewModel.conferirPreco(linha.chave) },
            erro = if (comErro && linha.item.isNotBlank()) estado.erro?.texto(contexto) else null,
        )
        linha.precoAcima?.let { preco ->
            Text(
                stringResource(R.string.formulario_preco_acima, preco.percentual.stripTrailingZeros().toPlainString(), formatarMoeda(preco.media, estado.moeda)),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (estado.outraMoeda) estado.convertido(linha.valor)?.let { Detalhe(stringResource(R.string.formulario_convertido, formatarMoeda(it, estado.moeda))) }
        CampoDeCategoria(
            estado.categorias,
            linha.categoria,
            { viewModel.mudarCategoria(linha.chave, it) },
            criar = { nome ->
                viewModel.criarCategoria(nome).recoverCatching { falha -> throw IllegalStateException(mensagemDeErro(falha).texto(contexto)) }
            },
            aoCriar = viewModel::categoriasCriadas,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            linha.item,
            { viewModel.mudarDescricao(linha.chave, it) },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.formulario_descricao)) },
            placeholder = { Text(stringResource(R.string.formulario_descricao_exemplo)) },
            singleLine = true,
            isError = comErro && linha.item.isBlank(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        if (estado.itens.size > 1) HorizontalDivider()
    }
}

@Composable
private fun ComoPagou(estado: EstadoDoFormulario, viewModel: FormularioDeGastoViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.formulario_como_pagou), style = MaterialTheme.typography.titleSmall)
        Escolhas(
            listOf(FormaDePagamento.CARTAO to R.string.formulario_cartao_de_credito, FormaDePagamento.DEBITO to R.string.formulario_debito_pix),
            estado.forma,
        ) { viewModel.trocarForma(it) }
        val faltouConta = estado.faltou == CampoQueFaltou.CONTA
        if (estado.forma == FormaDePagamento.CARTAO) {
            if (estado.cartoes.isEmpty()) {
                val abrirNoSite = lembrarAbrirNoSite()
                Detalhe(stringResource(R.string.formulario_nenhum_cartao))
                TextButton({ abrirNoSite("/contas-e-cartoes") }) { Text(stringResource(R.string.formulario_cadastrar_cartao)) }
            } else {
                Lista(
                    stringResource(R.string.formulario_cartao),
                    listOf("" to stringResource(R.string.formulario_escolha_o_cartao_opcao)) + estado.cartoes.map { it.id to it.nome },
                    estado.contaId,
                    faltouConta,
                    viewModel::escolherConta,
                )
                estado.fatura?.let { (mes, vencimento) ->
                    Detalhe(stringResource(R.string.formulario_vai_para_a_fatura, mesPorExtenso(mes), dataCurta(vencimento)))
                }
            }
        } else if (estado.bancos.isNotEmpty()) {
            Lista(
                stringResource(R.string.formulario_saiu_de_qual_conta),
                listOf("" to stringResource(R.string.formulario_escolha_a_conta_opcao)) + estado.bancos.map { it.id to it.nome },
                estado.contaId,
                faltouConta,
                viewModel::escolherConta,
            )
        }
    }
}

/** Débito/Pix é sempre à vista: o jeito da compra só aparece no cartão. */
@Composable
private fun ComoFoiACompra(estado: EstadoDoFormulario, viewModel: FormularioDeGastoViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.formulario_como_foi_a_compra), style = MaterialTheme.typography.titleSmall)
        Escolhas(
            listOf(
                ModoDaCompra.VISTA to R.string.formulario_a_vista,
                ModoDaCompra.PARCELADO to R.string.formulario_parcelado,
                ModoDaCompra.RECORRENTE to R.string.formulario_recorrente,
            ),
            estado.modo,
            viewModel::trocarModo,
        )
        when (estado.modo) {
            ModoDaCompra.PARCELADO -> {
                OutlinedTextField(
                    estado.parcelas,
                    viewModel::mudarParcelas,
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.formulario_quantas_parcelas)) },
                    singleLine = true,
                    isError = estado.faltou == CampoQueFaltou.PARCELAS,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                estado.valorDaParcela?.let { Detalhe(stringResource(R.string.formulario_valor_da_parcela, estado.parcelas, formatarMoeda(it, estado.moedaDaCompra))) }
            }
            ModoDaCompra.RECORRENTE -> Detalhe(stringResource(R.string.formulario_recorrente_explica, estado.diaDaCompra))
            ModoDaCompra.VISTA -> Unit
        }
    }
}

@Composable
private fun MaisOpcoes(estado: EstadoDoFormulario, viewModel: FormularioDeGastoViewModel) {
    val resumo = buildList {
        add(if (estado.ehHoje) stringResource(R.string.formulario_hoje) else dataCurta(estado.data.toString()))
        estado.estabelecimento.trim().takeIf { it.isNotEmpty() }?.let(::add)
        if (estado.outraMoeda) add(estado.moedaDaCompra)
        if (estado.dividirCom.isNotEmpty()) add(stringResource(R.string.formulario_dividido))
        if (estado.podeDividir && estado.compartilhadoEfetivo) add(stringResource(R.string.formulario_compartilhado))
    }.joinToString(" · ")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = viewModel::alternarMaisOpcoes),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.formulario_mais_opcoes), style = MaterialTheme.typography.titleSmall)
                Detalhe(resumo)
            }
            Icon(painterResource(if (estado.maisOpcoes) Icone.Subir.desenho else Icone.Descer.desenho), null)
        }
        if (!estado.maisOpcoes) return@Column

        CampoDeData(estado.data, viewModel::mudarData)
        OutlinedTextField(
            estado.estabelecimento,
            viewModel::mudarEstabelecimento,
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.formulario_estabelecimento)) },
            placeholder = { Text(stringResource(R.string.formulario_estabelecimento_exemplo)) },
            singleLine = true,
        )
        Lista(
            stringResource(R.string.formulario_moeda_da_compra),
            estado.moedasDaCompra.map { it to it },
            estado.moedaDaCompra,
            erro = false,
            aoEscolher = viewModel::trocarMoeda,
        )
        if (estado.outraMoeda) {
            Detalhe(
                estado.totalConvertido?.let { stringResource(R.string.formulario_convertido, formatarMoeda(it, estado.moeda)) }
                    ?: stringResource(R.string.formulario_buscando_cotacao),
            )
        }
        if (estado.podeDividir) {
            Text(stringResource(R.string.formulario_dividir_com), style = MaterialTheme.typography.titleSmall)
            estado.membros.forEach { membro ->
                Marcavel(membro.nome, membro.id in estado.dividirCom) { viewModel.alternarDivisao(membro.id) }
            }
            Marcavel(stringResource(R.string.formulario_compartilhar), estado.compartilhadoEfetivo, viewModel::mudarCompartilhado)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampoDeData(data: LocalDate, aoEscolher: (LocalDate) -> Unit) {
    var aberto by rememberSaveable { mutableStateOf(false) }
    val rotulo = stringResource(R.string.formulario_escolher_data)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            dataCurta(data.toString()),
            {},
            Modifier.weight(1f),
            readOnly = true,
            label = { Text(stringResource(R.string.formulario_data)) },
        )
        IconButton({ aberto = true }) { Icon(painterResource(Icone.Calendario.desenho), rotulo) }
    }
    if (!aberto) return
    // O DatePicker trabalha com meia-noite UTC: a conversão não desloca o dia.
    val seletor = rememberDatePickerState(initialSelectedDateMillis = data.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = { aberto = false },
        confirmButton = {
            TextButton({
                seletor.selectedDateMillis?.let { aoEscolher(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                aberto = false
            }) { Text(stringResource(R.string.formulario_ok)) }
        },
        dismissButton = { TextButton({ aberto = false }) { Text(stringResource(R.string.formulario_cancelar)) } },
    ) { DatePicker(seletor) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Escolhas(opcoes: List<Pair<T, Int>>, escolhida: T, aoEscolher: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        opcoes.forEachIndexed { indice, (valor, texto) ->
            SegmentedButton(
                selected = valor == escolhida,
                onClick = { aoEscolher(valor) },
                shape = SegmentedButtonDefaults.itemShape(indice, opcoes.size),
            ) { Text(stringResource(texto)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Lista(rotulo: String, opcoes: List<Pair<String, String>>, escolhida: String, erro: Boolean, aoEscolher: (String) -> Unit) {
    var aberta by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(aberta, { aberta = it }) {
        OutlinedTextField(
            opcoes.firstOrNull { it.first == escolhida }?.second.orEmpty(),
            {},
            Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            isError = erro,
            label = { Text(rotulo) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(aberta) },
        )
        ExposedDropdownMenu(aberta, { aberta = false }) {
            opcoes.forEach { (valor, nome) -> DropdownMenuItem({ Text(nome) }, { aoEscolher(valor); aberta = false }) }
        }
    }
}

@Composable
private fun Marcavel(texto: String, marcado: Boolean, aoMudar: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(marcado, role = Role.Checkbox, onValueChange = aoMudar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(marcado, null)
        Text(texto, Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun Detalhe(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
