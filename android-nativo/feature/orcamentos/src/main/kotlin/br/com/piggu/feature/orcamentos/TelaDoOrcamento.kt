package br.com.piggu.feature.orcamentos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import br.com.piggu.core.model.AlertaDoOrcamento
import br.com.piggu.core.model.Orcamento
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.AvisoDeGuardado
import br.com.piggu.core.ui.BarraDeProgresso
import br.com.piggu.core.ui.CampoDeCategoria
import br.com.piggu.core.ui.CampoDeValor
import br.com.piggu.core.ui.EstadoDeErro
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.FalhaAoCarregar
import br.com.piggu.core.ui.SeletorDeMes
import br.com.piggu.core.ui.Vazio
import br.com.piggu.core.ui.formatos.formatarMoeda
import br.com.piggu.core.ui.formatos.mesPorExtenso
import br.com.piggu.core.ui.formatos.simboloDaMoeda
import java.math.BigDecimal
import br.com.piggu.core.ui.R as Comum

data class EventosDoOrcamento(
    val voltar: () -> Unit = {},
    val abrirPremium: () -> Unit = {},
    val tentarDeNovo: () -> Unit = {},
    val mudarMesDoLimite: (String) -> Unit = {},
    val mudarLimite: (BigDecimal?) -> Unit = {},
    val salvarLimite: () -> Unit = {},
    val editarMeta: (String) -> Unit = {},
    val mudarCategoria: (String) -> Unit = {},
    val mudarLimiteDaCategoria: (BigDecimal?) -> Unit = {},
    val salvarCategoria: () -> Unit = {},
    val editarOrcamento: (Orcamento) -> Unit = {},
    val excluir: (Orcamento) -> Unit = {},
    val criarCategoria: suspend (String) -> Result<List<String>> = { Result.failure(UnsupportedOperationException()) },
    val categoriasCriadas: (List<String>) -> Unit = {},
)

/**
 * O Orçamento ligado ao [OrcamentoViewModel]: tela cheia, com o topo e o voltar. [abrirPremium]
 * leva ao plano quando quem não tem Premium quer pôr limite numa categoria.
 */
@Composable
fun TelaDoOrcamento(voltar: () -> Unit, abrirPremium: () -> Unit, modifier: Modifier = Modifier, viewModel: OrcamentoViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    TelaDoOrcamento(
        estado,
        EventosDoOrcamento(
            voltar = voltar,
            abrirPremium = abrirPremium,
            tentarDeNovo = viewModel::tentarDeNovo,
            mudarMesDoLimite = viewModel::mudarMesDoLimite,
            mudarLimite = viewModel::mudarLimite,
            salvarLimite = viewModel::salvarLimite,
            editarMeta = viewModel::editarMeta,
            mudarCategoria = viewModel::mudarCategoria,
            mudarLimiteDaCategoria = viewModel::mudarLimiteDaCategoria,
            salvarCategoria = viewModel::salvarCategoria,
            editarOrcamento = viewModel::editarOrcamento,
            excluir = viewModel::excluir,
            criarCategoria = viewModel::criarCategoria,
            categoriasCriadas = viewModel::categoriasCriadas,
        ),
        modifier,
    )
}

/** O Orçamento só desenhado (capturas e prévias). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDoOrcamento(estado: EstadoDoOrcamento, eventos: EventosDoOrcamento, modifier: Modifier = Modifier) {
    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.orcamento_titulo), Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(eventos.voltar) { Icon(painterResource(Icone.Esquerda.desenho), stringResource(Comum.string.voltar)) }
                },
            )
        },
    ) { margens ->
        Column(Modifier.padding(margens).fillMaxSize().wrapContentWidth().widthIn(max = 720.dp)) {
            // Fora da lista: o erro de salvar no fim da tela aparece sem rolar até o topo.
            estado.erro?.let { erro ->
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        erro.texto(LocalContext.current),
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp).semantics { liveRegion = LiveRegionMode.Assertive },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            estado.guardadoEm?.let { AvisoDeGuardado(it, estado.conectado) }
            when (val dados = estado.dados) {
                Carga.Carregando -> Esqueleto(linhas = 4)
                is Carga.Falhou -> EstadoDeErro(falhaDe(dados.erro), eventos.tentarDeNovo, mensagem = mensagemDeErro(dados.erro).texto(LocalContext.current))
                is Carga.Pronto -> LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item { LimiteDoMes(estado) }
                    item { DefinirLimite(estado, eventos) }
                    item { OutrosMeses(estado, eventos) }
                    item { PorCategoria(estado, eventos) }
                    item { NovoPorCategoria(estado, eventos) }
                }
            }
        }
    }
}

private fun falhaDe(erro: ErroDaApi): FalhaAoCarregar = when {
    erro is ErroDaApi.SemInternet -> FalhaAoCarregar.SemInternet
    erro is ErroDaApi.DoServidor && erro.status >= 500 -> FalhaAoCarregar.DoServidor
    else -> FalhaAoCarregar.Outra
}

@Composable
private fun Cartao(titulo: String, extra: @Composable () -> Unit = {}, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titulo, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                extra()
            }
            conteudo()
        }
    }
}

@Composable
private fun Detalhe(texto: String, cor: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Text(texto, style = MaterialTheme.typography.bodySmall, color = cor)

@Composable
private fun LimiteDoMes(estado: EstadoDoOrcamento) {
    Cartao(stringResource(R.string.orcamento_limite_do_mes)) {
        if (estado.metaDoMesAtual.signum() > 0) {
            val cor = if (estado.estourou) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Text(
                stringResource(R.string.orcamento_gasto_de_limite, formatarMoeda(estado.gastoDoMes, estado.moeda), formatarMoeda(estado.metaDoMesAtual, estado.moeda)),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            BarraDeProgresso(estado.progresso / 100f, cor = cor)
            Detalhe(
                stringResource(if (estado.estourou) R.string.orcamento_usado_estourou else R.string.orcamento_usado, estado.progresso),
                if (estado.estourou) cor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Vazio(
                Icone.Alvo,
                stringResource(R.string.orcamento_sem_limite_titulo),
                texto = stringResource(R.string.orcamento_sem_limite_texto),
                compacto = true,
            )
        }
    }
}

@Composable
private fun DefinirLimite(estado: EstadoDoOrcamento, eventos: EventosDoOrcamento) {
    Cartao(stringResource(R.string.orcamento_definir_limite)) {
        SeletorDeMes(estado.mesDoLimite, estado.ultimoMesDoLimite, eventos.mudarMesDoLimite, Modifier.align(Alignment.CenterHorizontally))
        CampoDeValor(
            estado.limite,
            eventos.mudarLimite,
            estado.moeda,
            Modifier.fillMaxWidth(),
            rotulo = stringResource(R.string.orcamento_total_no_mes, simboloDaMoeda(estado.moeda)),
        )
        Button(eventos.salvarLimite, Modifier.fillMaxWidth(), enabled = !estado.salvando) {
            Text(stringResource(if (estado.salvando) R.string.orcamento_guardando else R.string.orcamento_salvar_limite))
        }
    }
}

@Composable
private fun OutrosMeses(estado: EstadoDoOrcamento, eventos: EventosDoOrcamento) {
    Cartao(stringResource(R.string.orcamento_outros_meses)) {
        if (estado.outrosMeses.isEmpty()) {
            Vazio(Icone.Calendario, stringResource(R.string.orcamento_nenhum_limite), compacto = true)
        }
        estado.outrosMeses.forEach { (mes, limite) ->
            val nome = mesPorExtenso(mes)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(nome, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(formatarMoeda(limite, estado.moeda), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                IconButton({ eventos.editarMeta(mes) }) {
                    Icon(painterResource(Icone.Lapis.desenho), stringResource(R.string.orcamento_editar_mes, nome))
                }
            }
        }
    }
}

@Composable
private fun PorCategoria(estado: EstadoDoOrcamento, eventos: EventosDoOrcamento) {
    Cartao(stringResource(R.string.orcamento_por_categoria, mesPorExtenso(estado.mesAtual))) {
        if (estado.orcamentos.isEmpty()) {
            Vazio(
                Icone.Pizza,
                stringResource(R.string.orcamento_sem_categoria_titulo),
                texto = stringResource(R.string.orcamento_sem_categoria_texto),
                compacto = true,
            )
        }
        estado.orcamentos.forEach { orcamento -> LinhaDoOrcamento(orcamento, estado, eventos) }
    }
}

@Composable
private fun LinhaDoOrcamento(orcamento: Orcamento, estado: EstadoDoOrcamento, eventos: EventosDoOrcamento) {
    val (cor, alerta) = when (orcamento.alerta) {
        AlertaDoOrcamento.ESTOUROU -> MaterialTheme.colorScheme.error to R.string.orcamento_alerta_estourou
        AlertaDoOrcamento.ATENCAO -> MaterialTheme.colorScheme.tertiary to R.string.orcamento_alerta_atencao
        else -> MaterialTheme.colorScheme.primary to null
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(orcamento.categoria, style = MaterialTheme.typography.bodyLarge)
            Detalhe(
                stringResource(
                    R.string.orcamento_gasto_de_limite_percentual,
                    formatarMoeda(orcamento.gasto, estado.moeda),
                    formatarMoeda(orcamento.limite, estado.moeda),
                    orcamento.percentual.stripTrailingZeros().toPlainString(),
                ),
            )
            BarraDeProgresso(orcamento.percentual.toFloat() / 100f, cor = cor)
            alerta?.let { Detalhe(stringResource(it), cor) }
        }
        if (estado.premium) {
            IconButton({ eventos.editarOrcamento(orcamento) }) {
                Icon(painterResource(Icone.Lapis.desenho), stringResource(R.string.orcamento_mudar, orcamento.categoria))
            }
        }
        IconButton({ eventos.excluir(orcamento) }) {
            Icon(painterResource(Icone.Lixeira.desenho), stringResource(R.string.orcamento_apagar, orcamento.categoria), tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun NovoPorCategoria(estado: EstadoDoOrcamento, eventos: EventosDoOrcamento) {
    Cartao(
        stringResource(R.string.orcamento_limite_por_categoria),
        extra = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(Icone.Estrela.desenho), null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.orcamento_premium), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        },
    ) {
        if (estado.premium) {
            Detalhe(stringResource(R.string.orcamento_explicacao))
            CampoDeCategoria(
                estado.categorias,
                estado.categoria,
                eventos.mudarCategoria,
                eventos.criarCategoria,
                eventos.categoriasCriadas,
                Modifier.fillMaxWidth(),
                automatica = false,
            )
            CampoDeValor(
                estado.limiteDaCategoria,
                eventos.mudarLimiteDaCategoria,
                estado.moeda,
                Modifier.fillMaxWidth(),
                rotulo = stringResource(R.string.orcamento_limite_no_mes),
            )
            Button(eventos.salvarCategoria, Modifier.fillMaxWidth(), enabled = !estado.salvando) {
                Text(stringResource(R.string.orcamento_salvar_categoria))
            }
        } else {
            Text(stringResource(R.string.orcamento_convite), style = MaterialTheme.typography.bodyMedium)
            Button(eventos.abrirPremium, Modifier.fillMaxWidth()) { Text(stringResource(R.string.orcamento_conhecer_premium)) }
        }
    }
}
