package br.com.piggu.feature.plano

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
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
import br.com.piggu.core.model.InfoDoPlano
import br.com.piggu.core.model.OrigemDaAssinatura
import br.com.piggu.core.model.Plano
import br.com.piggu.core.model.Precos
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.AvisoDeGuardado
import br.com.piggu.core.ui.EstadoDeErro
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.FalhaAoCarregar
import br.com.piggu.core.ui.formatos.dataCurta
import br.com.piggu.core.ui.R as Comum

data class EventosDoPlano(
    val voltar: () -> Unit = {},
    val tentarDeNovo: () -> Unit = {},
    val pedirReembolso: () -> Unit = {},
    val confirmarReembolso: () -> Unit = {},
    val desistir: () -> Unit = {},
    /** Com `premium_mensal` ou `premium_anual`. */
    val assinar: (String) -> Unit = {},
    val restaurar: () -> Unit = {},
    val gerenciar: () -> Unit = {},
)

/** Premium ligada ao [PlanoViewModel]: tela cheia, só para o titular (o app barra os outros). */
@Composable
fun TelaDoPlano(voltar: () -> Unit, modifier: Modifier = Modifier, viewModel: PlanoViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val atividade = LocalActivity.current
    val navegador = LocalUriHandler.current
    val pacote = LocalContext.current.packageName
    TelaDoPlano(
        estado,
        EventosDoPlano(
            voltar = voltar,
            tentarDeNovo = viewModel::tentarDeNovo,
            pedirReembolso = viewModel::pedirReembolso,
            confirmarReembolso = viewModel::confirmarReembolso,
            desistir = viewModel::desistir,
            assinar = { produto -> atividade?.let { viewModel.assinar(it, produto) } },
            restaurar = viewModel::restaurar,
            // Sem o app da Play Store, openUri lança: o toque só não faz nada.
            gerenciar = { runCatching { navegador.openUri(enderecoDaAssinatura(estado.produtoDoPlay, pacote)) } },
        ),
        modifier,
    )
}

/** A tela só desenhada (capturas e prévias). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDoPlano(estado: EstadoDoPlano, eventos: EventosDoPlano, modifier: Modifier = Modifier) {
    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plano_titulo), Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(eventos.voltar) { Icon(painterResource(Icone.Esquerda.desenho), stringResource(Comum.string.voltar)) }
                },
            )
        },
    ) { margens ->
        Column(Modifier.padding(margens).fillMaxSize().wrapContentWidth().widthIn(max = 720.dp)) {
            estado.erro?.let { Faixa(it.texto(), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, LiveRegionMode.Assertive) }
            estado.aviso?.let { Faixa(stringResource(it), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, LiveRegionMode.Polite) }
            estado.guardadoEm?.let { AvisoDeGuardado(it, estado.conectado) }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (val carga = estado.plano) {
                    Carga.Carregando -> Esqueleto(linhas = 4)
                    is Carga.Falhou -> EstadoDeErro(falhaDe(carga.erro), eventos.tentarDeNovo, mensagem = mensagemDeErro(carga.erro).texto())
                    is Carga.Pronto -> Planos(carga.valor, estado, eventos)
                }
            }
        }
    }
    if (estado.confirmandoReembolso) {
        AlertDialog(
            onDismissRequest = eventos.desistir,
            text = { Text(stringResource(R.string.plano_reembolso_confirmar)) },
            confirmButton = {
                TextButton(eventos.confirmarReembolso, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.plano_reembolso_pedir))
                }
            },
            dismissButton = { TextButton(eventos.desistir) { Text(stringResource(R.string.plano_reembolso_manter)) } },
        )
    }
}

@Composable
private fun Planos(info: InfoDoPlano, estado: EstadoDoPlano, eventos: EventosDoPlano) {
    Situacao(info, estado, eventos)
    // Fora do quadro: quem assinou e trocou de aparelho (ou de conta) traz o Premium de volta.
    if (!estado.admin) {
        TextButton(eventos.restaurar, Modifier.heightIn(min = 48.dp), enabled = !estado.ocupado && estado.conectado) {
            Text(stringResource(R.string.plano_restaurar))
        }
    }

    CartaoDePlano(R.string.plano_gratuito, stringResource(R.string.plano_gratuito_etiqueta), estado.seuPlano == QualPlano.GRATUITO, null, info.gratuito)
    val loja = estado.precosDaLoja
    val precoDoPremium = info.app?.let { Precos(loja[PREMIUM_MENSAL] ?: emReais(it.mensal), loja[PREMIUM_ANUAL] ?: emReais(it.anual)) }
    CartaoDePlano(R.string.plano_premium, stringResource(R.string.plano_premium_etiqueta), estado.seuPlano == QualPlano.PREMIUM, precoDoPremium, info.premium) {
        if (estado.podeAssinar) {
            val livre = !estado.ocupado && estado.conectado
            Button({ eventos.assinar(PREMIUM_MENSAL) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = livre) {
                Text(stringResource(R.string.plano_assinar_mensal))
            }
            OutlinedButton({ eventos.assinar(PREMIUM_ANUAL) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = livre) {
                Text(stringResource(R.string.plano_assinar_anual))
            }
        }
    }
    info.appCasal?.let { precos ->
        CartaoDePlano(
            R.string.plano_casal, stringResource(R.string.plano_casal_etiqueta), estado.seuPlano == QualPlano.CASAL, precos.emReais(),
            listOf(R.string.plano_casal_item_1, R.string.plano_casal_item_2, R.string.plano_casal_item_3, R.string.plano_casal_item_4).map { stringResource(it) },
        )
    }
    CartaoDePlano(
        R.string.plano_familia, pluralStringResource(R.plurals.plano_familia_etiqueta, info.pessoasNoFamilia, info.pessoasNoFamilia), estado.seuPlano == QualPlano.FAMILIA,
        info.appFamilia.emReais(),
        listOf(
            stringResource(R.string.plano_familia_item_1),
            pluralStringResource(R.plurals.plano_familia_item_2, info.pessoasNoFamilia, info.pessoasNoFamilia),
            stringResource(R.string.plano_familia_item_3),
        ),
    )
    // Como no iOS, o app só vende o individual.
    if (estado.podeAssinar) Detalhe(stringResource(R.string.plano_so_individual))
    Detalhe(stringResource(R.string.plano_letras_miudas))
}

@Composable
private fun emReais(valor: String) = stringResource(R.string.plano_reais, valor)

@Composable
private fun Precos.emReais() = Precos(emReais(mensal), emReais(anual))

/** O quadro de cima: o Premium que a pessoa tem, ou por que ela já tem (ou não pode assinar). */
@Composable
private fun Situacao(info: InfoDoPlano, estado: EstadoDoPlano, eventos: EventosDoPlano) {
    if (info.plano != Plano.PREMIUM) {
        val aviso = when {
            estado.admin -> R.string.plano_admin
            info.casaTemAssinatura && info.planoCasal == true -> R.string.plano_casa_tem_casal
            info.casaTemAssinatura -> R.string.plano_casa_tem_individual
            else -> return
        }
        Card(Modifier.fillMaxWidth()) { Text(stringResource(aviso), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
        return
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(Icone.Estrela.desenho), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.plano_voce_tem, stringResource(nomeDoPlano(estado.seuPlano))),
                    Modifier.padding(start = 8.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            info.premiumAte?.let { Text(stringResource(R.string.plano_ate, dataCurta(it)), style = MaterialTheme.typography.bodyMedium) }
            when {
                !info.voceAssinou -> Detalhe(
                    stringResource(if (info.planoCasal == true) R.string.plano_quem_assinou_casal else R.string.plano_quem_assinou_familia),
                )
                info.origem == OrigemDaAssinatura.WEB -> {
                    Detalhe(stringResource(R.string.plano_origem_site))
                    info.reembolsoAte?.let { ate ->
                        OutlinedButton(
                            eventos.pedirReembolso,
                            Modifier.heightIn(min = 48.dp),
                            enabled = !estado.ocupado && estado.conectado,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(stringResource(R.string.plano_reembolso_pedir)) }
                        Detalhe(stringResource(R.string.plano_reembolso_ate, dataCurta(ate)))
                    }
                }
                info.origem == OrigemDaAssinatura.APP_STORE -> Detalhe(stringResource(R.string.plano_origem_app_store))
                info.origem == OrigemDaAssinatura.PLAY_STORE -> {
                    Detalhe(stringResource(R.string.plano_origem_play_store))
                    OutlinedButton(eventos.gerenciar, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.plano_gerenciar)) }
                }
            }
        }
    }
}

private fun nomeDoPlano(plano: QualPlano?) = when (plano) {
    QualPlano.FAMILIA -> R.string.plano_nome_familia
    QualPlano.CASAL -> R.string.plano_nome_casal
    else -> R.string.plano_nome_individual
}

@Composable
private fun CartaoDePlano(
    titulo: Int,
    etiqueta: String,
    atual: Boolean,
    precos: Precos?,
    itens: List<String>,
    acoes: @Composable ColumnScope.() -> Unit = {},
) {
    val borda = if (atual) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    OutlinedCard(Modifier.fillMaxWidth(), border = borda) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (atual) {
                Text(stringResource(R.string.plano_seu_plano), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(titulo), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            precos?.let {
                Text(stringResource(R.string.plano_preco_mensal, it.mensal), style = MaterialTheme.typography.titleLarge)
                Detalhe(stringResource(R.string.plano_preco_anual, it.anual))
            }
            itens.forEach { item ->
                Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
                    Icon(painterResource(Icone.Check.desenho), null, Modifier.padding(top = 2.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(item, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            acoes()
        }
    }
}

@Composable
private fun Detalhe(texto: String) =
    Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Faixa(texto: String, fundo: Color, cor: Color, anuncio: LiveRegionMode) {
    Surface(Modifier.fillMaxWidth(), color = fundo) {
        Text(
            texto,
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp).semantics { liveRegion = anuncio },
            style = MaterialTheme.typography.bodyMedium,
            color = cor,
        )
    }
}

internal fun falhaDe(erro: ErroDaApi): FalhaAoCarregar = when {
    erro is ErroDaApi.SemInternet -> FalhaAoCarregar.SemInternet
    erro is ErroDaApi.DoServidor && erro.status >= 500 -> FalhaAoCarregar.DoServidor
    else -> FalhaAoCarregar.Outra
}

@Composable
private fun MensagemDeErro.texto(): String = texto(LocalContext.current)
