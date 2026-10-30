package br.com.piggu.feature.receitas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.model.AReceber
import br.com.piggu.core.model.ContaFixa
import br.com.piggu.core.model.Receita
import br.com.piggu.core.ui.AvisoDeGuardado
import br.com.piggu.core.ui.CampoDeValor
import br.com.piggu.core.ui.EstadoDeErro
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.SeletorDeMes
import br.com.piggu.core.ui.Vazio
import br.com.piggu.core.ui.formatos.dataCurta
import br.com.piggu.core.ui.formatos.formatarMoeda
import java.math.BigDecimal
import br.com.piggu.core.ui.R as Comum

data class EventosDeReceitas(
    val lancarReceita: () -> Unit = {},
    val mudarMes: (String) -> Unit = {},
    val tentarDeNovo: () -> Unit = {},
    val pedirParaApagar: (Receita) -> Unit = {},
    val pedirParaPararDeRepetir: (ContaFixa) -> Unit = {},
    val pedirParaApagarAReceber: (AReceber) -> Unit = {},
    val confirmar: () -> Unit = {},
    val desistir: () -> Unit = {},
    val receber: (AReceber) -> Unit = {},
    val mudarQuem: (String) -> Unit = {},
    val mudarMotivo: (String) -> Unit = {},
    val mudarValorAReceber: (BigDecimal?) -> Unit = {},
    val mudarDataAReceber: (String) -> Unit = {},
    val guardarAReceber: () -> Unit = {},
)

/**
 * A aba Receitas ligada ao [ReceitasViewModel]. [lancarReceita] abre o [FormularioDeReceita]
 * (a mesma folha do + do app); a lista recarrega sozinha quando a receita é lançada.
 */
@Composable
fun TelaDeReceitas(lancarReceita: () -> Unit, modifier: Modifier = Modifier, viewModel: ReceitasViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    TelaDeReceitas(
        estado,
        EventosDeReceitas(
            lancarReceita = lancarReceita,
            mudarMes = viewModel::mudarMes,
            tentarDeNovo = viewModel::tentarDeNovo,
            pedirParaApagar = viewModel::pedirParaApagar,
            pedirParaPararDeRepetir = viewModel::pedirParaPararDeRepetir,
            pedirParaApagarAReceber = viewModel::pedirParaApagarAReceber,
            confirmar = viewModel::confirmar,
            desistir = viewModel::desistir,
            receber = viewModel::receber,
            mudarQuem = viewModel::mudarQuem,
            mudarMotivo = viewModel::mudarMotivo,
            mudarValorAReceber = viewModel::mudarValorAReceber,
            mudarDataAReceber = viewModel::mudarDataAReceber,
            guardarAReceber = viewModel::guardarAReceber,
        ),
        modifier,
    )
}

/** A aba só desenhada (capturas e prévias). */
@Composable
fun TelaDeReceitas(estado: EstadoDeReceitas, eventos: EventosDeReceitas, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().wrapContentWidth().widthIn(max = 720.dp)) {
        SeletorDeMes(estado.mes, estado.mesAtual, eventos.mudarMes, Modifier.align(Alignment.CenterHorizontally))
        // Fora da lista: o aviso de uma ação no fim da tela aparece sem rolar até o topo.
        estado.erro?.let { Faixa(it.texto(), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, LiveRegionMode.Assertive) }
        estado.aviso?.let { Faixa(it.texto(), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, LiveRegionMode.Polite) }
        estado.guardadoEm?.let { AvisoDeGuardado(it, estado.conectado) }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                OutlinedButton(eventos.lancarReceita, Modifier.fillMaxWidth()) {
                    Icon(painterResource(Icone.Mais.desenho), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.receitas_nova))
                }
            }
            item { DoMes(estado, eventos) }
            if (estado.recorrentes.isNotEmpty()) item { EntramTodoMes(estado, eventos) }
            item { AReceberCartao(estado, eventos) }
        }
    }
    estado.confirmacao?.let { Confirmar(it, eventos) }
}

@Composable
private fun Faixa(texto: String, fundo: androidx.compose.ui.graphics.Color, cor: androidx.compose.ui.graphics.Color, anuncio: LiveRegionMode) {
    Surface(Modifier.fillMaxWidth(), color = fundo) {
        Text(
            texto,
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp).semantics { liveRegion = anuncio },
            style = MaterialTheme.typography.bodyMedium,
            color = cor,
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

/** Uma linha: título e detalhe à esquerda (lidos juntos), o valor e as ações à direita. */
@Composable
private fun Linha(
    titulo: String,
    detalhe: String,
    valor: String,
    corDoDetalhe: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    acoes: @Composable () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Text(detalhe, style = MaterialTheme.typography.bodySmall, color = corDoDetalhe)
            Text(valor, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        acoes()
    }
}

@Composable
private fun Lixeira(descricao: String, aoTocar: () -> Unit) {
    IconButton(aoTocar) {
        Icon(painterResource(Icone.Lixeira.desenho), descricao, tint = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun DoMes(estado: EstadoDeReceitas, eventos: EventosDeReceitas) {
    when (val carga = estado.receitas) {
        Carga.Carregando -> Esqueleto(linhas = 3)
        is Carga.Falhou -> EstadoDeErro(falhaDe(carga.erro), eventos.tentarDeNovo, mensagem = br.com.piggu.core.network.mensagemDeErro(carga.erro).texto())
        is Carga.Pronto -> if (carga.valor.isEmpty()) {
            Vazio(
                Icone.Entrada,
                stringResource(R.string.receitas_vazio_titulo),
                texto = stringResource(R.string.receitas_vazio_texto),
                acao = stringResource(R.string.receitas_vazio_acao) to eventos.lancarReceita,
            )
        } else {
            Cartao(stringResource(R.string.receitas_total, formatarMoeda(estado.total, estado.moeda))) {
                carga.valor.forEach { receita ->
                    val partes = listOfNotNull(
                        dataCurta(receita.data),
                        receita.categoria,
                        stringResource(R.string.receitas_compartilhada).takeIf { receita.compartilhado == true },
                        estado.deQuem(receita)?.texto(),
                    )
                    Linha(receita.descricao, partes.joinToString(" · "), formatarMoeda(receita.valor, estado.moeda)) {
                        if (estado.podeApagar(receita)) Lixeira(stringResource(R.string.receitas_apagar)) { eventos.pedirParaApagar(receita) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntramTodoMes(estado: EstadoDeReceitas, eventos: EventosDeReceitas) {
    Cartao(stringResource(R.string.receitas_entram_todo_mes)) {
        estado.recorrentes.forEach { fixa ->
            val segundoDia = fixa.segundoDia
            val segundoValor = fixa.segundoValor
            val detalhe = if (segundoDia != null && segundoValor != null) {
                stringResource(
                    R.string.receitas_duas_parcelas_detalhe,
                    fixa.dia, formatarMoeda(fixa.valor, estado.moeda), segundoDia, formatarMoeda(segundoValor, estado.moeda), fixa.categoria,
                )
            } else {
                stringResource(R.string.receitas_todo_dia, fixa.dia, fixa.categoria)
            }
            Linha(fixa.descricao, detalhe, formatarMoeda(fixa.valor + (segundoValor ?: BigDecimal.ZERO), estado.moeda)) {
                Lixeira(stringResource(R.string.receitas_parar_de_repetir)) { eventos.pedirParaPararDeRepetir(fixa) }
            }
        }
    }
}

@Composable
private fun AReceberCartao(estado: EstadoDeReceitas, eventos: EventosDeReceitas) {
    Cartao(stringResource(R.string.a_receber_titulo)) {
        Text(stringResource(R.string.a_receber_explicacao), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        estado.aReceber.forEach { conta ->
            val atrasada = estado.atrasada(conta)
            Linha(
                listOfNotNull(conta.quem, conta.descricao?.takeIf { it.isNotEmpty() }).joinToString(" · "),
                stringResource(if (atrasada) R.string.a_receber_devendo_desde else R.string.a_receber_combinado, dataCurta(conta.data)),
                formatarMoeda(conta.valor, estado.moeda),
                corDoDetalhe = if (atrasada) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                TextButton({ eventos.receber(conta) }) { Text(stringResource(R.string.a_receber_recebi)) }
                Lixeira(stringResource(R.string.receitas_apagar)) { eventos.pedirParaApagarAReceber(conta) }
            }
        }
        if (estado.aReceber.isEmpty()) {
            Text(stringResource(R.string.a_receber_vazio), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            estado.quem,
            eventos.mudarQuem,
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.a_receber_quem)) },
            placeholder = { Text(stringResource(R.string.a_receber_quem_exemplo)) },
            singleLine = true,
            isError = estado.faltouNoAReceber == CampoDoAReceber.Quem,
        )
        OutlinedTextField(
            estado.motivo,
            eventos.mudarMotivo,
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.a_receber_motivo)) },
            placeholder = { Text(stringResource(R.string.a_receber_motivo_exemplo)) },
            singleLine = true,
        )
        CampoDeValor(
            estado.valorAReceber,
            eventos.mudarValorAReceber,
            estado.moeda,
            Modifier.fillMaxWidth(),
            erro = estado.erro?.texto()?.takeIf { estado.faltouNoAReceber == CampoDoAReceber.Valor },
        )
        CampoDeData(stringResource(R.string.a_receber_data), estado.dataAReceber, eventos.mudarDataAReceber, Modifier.fillMaxWidth())
        OutlinedButton(eventos.guardarAReceber, Modifier.fillMaxWidth(), enabled = !estado.guardando) {
            Text(stringResource(R.string.a_receber_guardar))
        }
    }
}

@Composable
private fun Confirmar(confirmacao: Confirmacao, eventos: EventosDeReceitas) {
    val (pergunta, botao) = when (confirmacao) {
        is Confirmacao.ApagarReceita ->
            stringResource(R.string.receitas_apagar_pergunta, confirmacao.receita.descricao) to R.string.receitas_apagar
        is Confirmacao.PararDeRepetir ->
            stringResource(R.string.receitas_parar_pergunta, confirmacao.fixa.descricao) to R.string.receitas_parar_de_repetir
        is Confirmacao.ApagarAReceber ->
            stringResource(R.string.a_receber_apagar_pergunta, confirmacao.conta.quem) to R.string.receitas_apagar
    }
    AlertDialog(
        onDismissRequest = eventos.desistir,
        confirmButton = {
            TextButton(eventos.confirmar) { Text(stringResource(botao), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(eventos.desistir) { Text(stringResource(Comum.string.cancelar)) } },
        text = { Text(pergunta) },
    )
}
