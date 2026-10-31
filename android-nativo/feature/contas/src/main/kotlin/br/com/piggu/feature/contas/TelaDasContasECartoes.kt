package br.com.piggu.feature.contas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.model.ContaOuCartao
import br.com.piggu.core.model.TipoDeConta
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.AvisoDeGuardado
import br.com.piggu.core.ui.EstadoDeErro
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.Vazio
import br.com.piggu.core.ui.abrirNoSite
import br.com.piggu.core.ui.formatos.dataCurta
import br.com.piggu.core.ui.formatos.formatarMoeda
import br.com.piggu.core.ui.formatos.mesPorExtenso
import br.com.piggu.core.ui.R as Comum

data class EventosDasContasECartoes(
    val voltar: () -> Unit = {},
    val tentarDeNovo: () -> Unit = {},
    val mudarNome: (String) -> Unit = {},
    val mudarTipo: (TipoDeConta) -> Unit = {},
    val mudarVencimento: (String) -> Unit = {},
    val mudarFechamento: (String) -> Unit = {},
    val mudarCompartilhada: (Boolean) -> Unit = {},
    val salvar: () -> Unit = {},
    val alternarCompartilhamento: (ContaOuCartao) -> Unit = {},
    val pedirParaApagar: (ContaOuCartao) -> Unit = {},
    val verFatura: (ContaOuCartao) -> Unit = {},
    val faturaAnterior: () -> Unit = {},
    val proximaFatura: () -> Unit = {},
    val fecharFatura: () -> Unit = {},
    val alternarPagamento: () -> Unit = {},
    val conectarBanco: () -> Unit = {},
    val atualizarBancos: () -> Unit = {},
    val pedirParaDesconectar: (conexaoId: String, instituicao: String) -> Unit = { _, _ -> },
    val confirmar: () -> Unit = {},
    val desistir: () -> Unit = {},
)

/**
 * Cartões e bancos, tela cheia ligada ao [ContasECartoesViewModel]. Conectar banco abre a página
 * /conectar-banco do site num Custom Tab (o widget da Pluggy só existe lá, D-117): passa pela
 * senha do banco, então nunca num WebView.
 */
@Composable
fun TelaDasContasECartoes(
    voltar: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContasECartoesViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val cor = MaterialTheme.colorScheme.surface.toArgb()
    TelaDasContasECartoes(
        estado,
        EventosDasContasECartoes(
            voltar = voltar,
            tentarDeNovo = viewModel::tentarDeNovo,
            mudarNome = viewModel::mudarNome,
            mudarTipo = viewModel::mudarTipo,
            mudarVencimento = viewModel::mudarVencimento,
            mudarFechamento = viewModel::mudarFechamento,
            mudarCompartilhada = viewModel::mudarCompartilhada,
            salvar = viewModel::salvar,
            alternarCompartilhamento = viewModel::alternarCompartilhamento,
            pedirParaApagar = viewModel::pedirParaApagar,
            verFatura = viewModel::verFatura,
            faturaAnterior = viewModel::faturaAnterior,
            proximaFatura = viewModel::proximaFatura,
            fecharFatura = viewModel::fecharFatura,
            alternarPagamento = viewModel::alternarPagamento,
            conectarBanco = { viewModel.conectarBanco { caminho -> abrirNoSite(contexto, caminho, cor, voltaParaOApp = true) } },
            atualizarBancos = viewModel::atualizarBancos,
            pedirParaDesconectar = viewModel::pedirParaDesconectar,
            confirmar = viewModel::confirmar,
            desistir = viewModel::desistir,
        ),
        modifier,
    )
}

/** A tela só desenhada (capturas e prévias). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDasContasECartoes(estado: EstadoDasContasECartoes, eventos: EventosDasContasECartoes, modifier: Modifier = Modifier) {
    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contas_titulo), Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(eventos.voltar) { Icon(painterResource(Icone.Esquerda.desenho), stringResource(Comum.string.voltar)) }
                },
            )
        },
    ) { margens ->
        Column(Modifier.padding(margens).fillMaxSize().wrapContentWidth().widthIn(max = 720.dp)) {
            // Fora da lista: o erro de uma ação no fim da tela aparece sem rolar até o topo.
            if (estado.fatura == null) estado.erro?.let { Faixa(it.texto()) }
            estado.guardadoEm?.let { AvisoDeGuardado(it, estado.conectado) }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { SuasContas(estado, eventos) }
                if (estado.contas is Carga.Pronto) item { NovaContaOuCartao(estado, eventos) }
                if (estado.mostrarBancos) item { Bancos(estado, eventos) }
            }
        }
    }
    estado.fatura?.let { FaturaDoCartao(it, estado, eventos) }
    estado.confirmacao?.let { Confirmar(it, eventos) }
}

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
private fun Cartao(titulo: String, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            conteudo()
        }
    }
}

@Composable
private fun Detalhe(texto: String, modifier: Modifier = Modifier, cor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(texto, modifier, style = MaterialTheme.typography.bodySmall, color = cor)
}

@Composable
private fun SuasContas(estado: EstadoDasContasECartoes, eventos: EventosDasContasECartoes) {
    when (val carga = estado.contas) {
        Carga.Carregando -> Esqueleto(linhas = 3)
        is Carga.Falhou -> EstadoDeErro(falhaDe(carga.erro), eventos.tentarDeNovo, mensagem = mensagemDeErro(carga.erro).texto())
        is Carga.Pronto -> if (carga.valor.isEmpty()) {
            Vazio(Icone.Cartao, stringResource(R.string.contas_vazio_titulo), texto = stringResource(R.string.contas_vazio_texto))
        } else {
            Cartao(stringResource(R.string.contas_suas)) {
                carga.valor.forEach { LinhaDaConta(it, estado, eventos) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinhaDaConta(conta: ContaOuCartao, estado: EstadoDasContasECartoes, eventos: EventosDasContasECartoes) {
    val cartao = conta.tipo == TipoDeConta.CARTAO
    Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(38.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource((if (cartao) Icone.Cartao else Icone.Banco).desenho), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(conta.nome, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (conta.compartilhado == true) Detalhe(stringResource(R.string.contas_do_casal), cor = MaterialTheme.colorScheme.primary)
                estado.deQuem(conta)?.let { Detalhe(it.texto()) }
                if (!cartao) {
                    Detalhe(stringResource(R.string.contas_tipo_conta))
                } else {
                    Detalhe(stringResource(R.string.contas_fecha_vence, conta.fechamento ?: 0, conta.vencimento ?: 0))
                    conta.faturaAberta?.let { f ->
                        Detalhe(stringResource(R.string.contas_fatura_aberta, mesPorExtenso(f.mes), formatarMoeda(f.total, estado.moeda)))
                        if (f.ultimaCompra != null && f.fechamento != null) {
                            Detalhe(
                                stringResource(R.string.contas_destaque_fechamento, dataCurta(f.ultimaCompra), dataCurta(f.fechamento)),
                                cor = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    conta.faturaAPagar?.let { f ->
                        Detalhe(
                            if (f.paga == true) {
                                stringResource(R.string.contas_fatura_paga, mesPorExtenso(f.mes))
                            } else {
                                stringResource(R.string.contas_a_pagar, formatarMoeda(f.total, estado.moeda), dataCurta(f.vencimento))
                            },
                        )
                    }
                }
            }
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
            if (cartao && conta.faturaAberta != null) {
                OutlinedButton({ eventos.verFatura(conta) }) { Text(stringResource(R.string.contas_ver_fatura)) }
            }
            if (estado.podeCompartilhar(conta)) {
                OutlinedButton({ eventos.alternarCompartilhamento(conta) }) {
                    Text(stringResource(if (conta.compartilhado == true) R.string.contas_deixar_so_meu else R.string.contas_compartilhar))
                }
            }
            if (estado.minha(conta)) {
                IconButton({ eventos.pedirParaApagar(conta) }) {
                    Icon(painterResource(Icone.Lixeira.desenho), stringResource(R.string.contas_apagar_conta, conta.nome), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovaContaOuCartao(estado: EstadoDasContasECartoes, eventos: EventosDasContasECartoes) {
    val cartao = estado.tipo == TipoDeConta.CARTAO
    Cartao(stringResource(R.string.nova_conta_titulo)) {
        OutlinedTextField(
            estado.nome,
            eventos.mudarNome,
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.nova_conta_nome)) },
            placeholder = { Text(stringResource(R.string.nova_conta_nome_exemplo)) },
            singleLine = true,
            isError = estado.faltou == CampoDaConta.Nome,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        val tipos = listOf(TipoDeConta.CARTAO to R.string.nova_conta_cartao, TipoDeConta.CONTA to R.string.nova_conta_conta)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            tipos.forEachIndexed { i, (tipo, rotulo) ->
                SegmentedButton(estado.tipo == tipo, { eventos.mudarTipo(tipo) }, SegmentedButtonDefaults.itemShape(i, tipos.size)) {
                    Text(stringResource(rotulo))
                }
            }
        }
        if (cartao) {
            CampoDeDia(stringResource(R.string.nova_conta_vencimento), estado.vencimento, eventos.mudarVencimento, estado.faltou == CampoDaConta.Vencimento)
            CampoDeDia(stringResource(R.string.nova_conta_fechamento), estado.fechamento, eventos.mudarFechamento, estado.faltou == CampoDaConta.Fechamento)
            Detalhe(stringResource(R.string.nova_conta_dica))
        }
        if (estado.temCompanhia) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(estado.compartilhada, role = Role.Checkbox, onValueChange = eventos.mudarCompartilhada),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(estado.compartilhada, onCheckedChange = null)
                Text(stringResource(R.string.nova_conta_do_casal), Modifier.padding(start = 8.dp))
            }
        }
        Button(eventos.salvar, Modifier.fillMaxWidth(), enabled = !estado.salvando) { Text(stringResource(R.string.nova_conta_salvar)) }
    }
}

@Composable
private fun CampoDeDia(rotulo: String, dia: String, aoMudar: (String) -> Unit, erro: Boolean) {
    OutlinedTextField(
        dia,
        aoMudar,
        Modifier.fillMaxWidth(),
        label = { Text(rotulo) },
        singleLine = true,
        isError = erro,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Bancos(estado: EstadoDasContasECartoes, eventos: EventosDasContasECartoes) {
    Cartao(stringResource(R.string.cartoes_bancos_titulo)) {
        if (estado.bancos.isEmpty()) Detalhe(stringResource(R.string.cartoes_bancos_vazio))
        estado.bancos.forEach { conta ->
            Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(conta.nome, style = MaterialTheme.typography.bodyLarge)
                    Detalhe(listOfNotNull(conta.instituicao, conta.numero?.takeIf { it.isNotEmpty() }).joinToString(" · "))
                }
                Text(formatarMoeda(conta.saldo, conta.moeda), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        estado.conexoes.forEach { (conexaoId, instituicao) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Detalhe(stringResource(R.string.cartoes_bancos_conectado, instituicao), Modifier.weight(1f))
                OutlinedButton({ eventos.pedirParaDesconectar(conexaoId, instituicao) }, enabled = !estado.mexendoNosBancos) {
                    Text(stringResource(R.string.cartoes_bancos_desconectar))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (estado.openFinanceLigado) {
                OutlinedButton(eventos.conectarBanco) { Text(stringResource(R.string.cartoes_bancos_conectar)) }
            }
            if (estado.bancos.isNotEmpty()) {
                TextButton(eventos.atualizarBancos, enabled = !estado.mexendoNosBancos) {
                    Text(stringResource(if (estado.mexendoNosBancos) R.string.cartoes_bancos_aguarde else R.string.cartoes_bancos_atualizar))
                }
            }
        }
        if (estado.openFinanceLigado) Detalhe(stringResource(Comum.string.abre_no_site))
    }
}

/** A fatura de um cartão numa folha, andando para a anterior e a seguinte, com os gastos dela. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FaturaDoCartao(aberta: FaturaNaTela, estado: EstadoDasContasECartoes, eventos: EventosDasContasECartoes) {
    ModalBottomSheet(eventos.fecharFatura, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(eventos.faturaAnterior) { Icon(painterResource(Icone.Esquerda.desenho), stringResource(R.string.fatura_anterior)) }
                    Text(
                        stringResource(R.string.fatura_titulo, aberta.cartao.nome, mesPorExtenso(aberta.mes)),
                        Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(eventos.proximaFatura) { Icon(painterResource(Icone.Direita.desenho), stringResource(R.string.fatura_proxima)) }
                }
            }
            estado.erro?.let { item { Faixa(it.texto()) } }
            when (val carga = aberta.carga) {
                Carga.Carregando -> item { Esqueleto(linhas = 3) }
                is Carga.Falhou -> item { EstadoDeErro(falhaDe(carga.erro), eventos.tentarDeNovo, mensagem = mensagemDeErro(carga.erro).texto()) }
                is Carga.Pronto -> {
                    val fatura = carga.valor.fatura
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (fatura.inicio != null && fatura.ultimaCompra != null && fatura.fechamento != null) {
                                Detalhe(
                                    stringResource(
                                        R.string.fatura_periodo,
                                        dataCurta(fatura.inicio), dataCurta(fatura.ultimaCompra), dataCurta(fatura.fechamento), dataCurta(fatura.vencimento),
                                    ),
                                )
                            }
                            Text(formatarMoeda(fatura.total, estado.moeda), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            if (fatura.paga == true) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                        Text(stringResource(R.string.fatura_paga), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
                                    }
                                    OutlinedButton(eventos.alternarPagamento, enabled = !estado.pagando) { Text(stringResource(R.string.fatura_desfazer)) }
                                }
                            } else {
                                Button(eventos.alternarPagamento, enabled = !estado.pagando) { Text(stringResource(R.string.fatura_marcar_paga)) }
                            }
                        }
                    }
                    if (carga.valor.gastos.isEmpty()) item { Detalhe(stringResource(R.string.fatura_vazia)) }
                    carga.valor.gastos.forEach { gasto ->
                        item(gasto.id) {
                            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(gasto.item, style = MaterialTheme.typography.bodyLarge)
                                    val parcela = gasto.parcelas?.let { stringResource(R.string.fatura_parcela, gasto.parcela ?: 0, it) }
                                    Detalhe(listOfNotNull(dataCurta(gasto.data), gasto.categoria, parcela).joinToString(" · "))
                                }
                                Text(formatarMoeda(gasto.valor, estado.moeda), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Confirmar(confirmacao: Confirmacao, eventos: EventosDasContasECartoes) {
    val (pergunta, botao) = when (confirmacao) {
        is Confirmacao.ApagarConta -> stringResource(R.string.contas_apagar_pergunta, confirmacao.conta.nome) to R.string.contas_apagar
        is Confirmacao.Desconectar -> stringResource(R.string.cartoes_bancos_desconectar_pergunta, confirmacao.instituicao) to R.string.cartoes_bancos_desconectar
    }
    AlertDialog(
        onDismissRequest = eventos.desistir,
        confirmButton = { TextButton(eventos.confirmar) { Text(stringResource(botao), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(eventos.desistir) { Text(stringResource(Comum.string.cancelar)) } },
        text = { Text(pergunta) },
    )
}
