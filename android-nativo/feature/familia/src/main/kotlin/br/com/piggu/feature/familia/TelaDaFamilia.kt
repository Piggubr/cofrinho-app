package br.com.piggu.feature.familia

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.model.AcaoDeAuditoria
import br.com.piggu.core.model.ConviteDaFamilia
import br.com.piggu.core.model.Familia
import br.com.piggu.core.model.MembroDaFamilia
import br.com.piggu.core.model.PigguRole
import br.com.piggu.core.model.Plano
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.AvisoDeGuardado
import br.com.piggu.core.ui.EstadoDeErro
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.FalhaAoCarregar
import br.com.piggu.core.ui.Vazio
import br.com.piggu.core.ui.formatos.dataCurta
import br.com.piggu.core.ui.R as Comum

data class EventosDaFamilia(
    val voltar: () -> Unit = {},
    val abrirPremium: () -> Unit = {},
    val tentarDeNovo: () -> Unit = {},
    val mudarNome: (String) -> Unit = {},
    val renomear: () -> Unit = {},
    val mudarEmail: (String) -> Unit = {},
    val convidar: () -> Unit = {},
    val cancelarConvite: (ConviteDaFamilia) -> Unit = {},
    val mudarPapel: (MembroDaFamilia) -> Unit = {},
    val pedirParaRemover: (MembroDaFamilia) -> Unit = {},
    val pedirParaAceitarConvite: (ConviteDaFamilia) -> Unit = {},
    val pedirParaAceitarParceria: () -> Unit = {},
    val recusarParceria: () -> Unit = {},
    val pedirParaSair: () -> Unit = {},
    val verHistorico: () -> Unit = {},
    val confirmar: () -> Unit = {},
    val desistir: () -> Unit = {},
)

/** Família ligada ao [FamiliaViewModel]: tela cheia, aberta pelo Mais ou pelo Perfil. */
@Composable
fun TelaDaFamilia(voltar: () -> Unit, abrirPremium: () -> Unit, modifier: Modifier = Modifier, viewModel: FamiliaViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    TelaDaFamilia(
        estado,
        EventosDaFamilia(
            voltar = voltar,
            abrirPremium = abrirPremium,
            tentarDeNovo = viewModel::tentarDeNovo,
            mudarNome = viewModel::mudarNome,
            renomear = viewModel::renomear,
            mudarEmail = viewModel::mudarEmail,
            convidar = viewModel::convidar,
            cancelarConvite = viewModel::cancelarConvite,
            mudarPapel = viewModel::mudarPapel,
            pedirParaRemover = viewModel::pedirParaRemover,
            pedirParaAceitarConvite = viewModel::pedirParaAceitarConvite,
            pedirParaAceitarParceria = viewModel::pedirParaAceitarParceria,
            recusarParceria = viewModel::recusarParceria,
            pedirParaSair = viewModel::pedirParaSair,
            verHistorico = viewModel::verHistorico,
            confirmar = viewModel::confirmar,
            desistir = viewModel::desistir,
        ),
        modifier,
    )
}

/** A tela só desenhada (capturas e prévias). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDaFamilia(estado: EstadoDaFamilia, eventos: EventosDaFamilia, modifier: Modifier = Modifier) {
    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.familia_titulo), Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(eventos.voltar) { Icon(painterResource(Icone.Esquerda.desenho), stringResource(Comum.string.voltar)) }
                },
            )
        },
    ) { margens ->
        Column(Modifier.padding(margens).fillMaxSize().wrapContentWidth().widthIn(max = 720.dp)) {
            estado.erro?.let { Faixa(it.texto(), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, LiveRegionMode.Assertive) }
            estado.aviso?.let {
                val texto = if (it.nome != null) stringResource(it.texto, it.nome) else stringResource(it.texto)
                Faixa(texto, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, LiveRegionMode.Polite)
            }
            estado.guardadoEm?.let { AvisoDeGuardado(it, estado.conectado) }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(estado.convitesParaMim, key = { "recebido-${it.id}" }) { ConviteRecebido(it, estado, eventos) }
                when (val carga = estado.familia) {
                    Carga.Carregando -> item { Esqueleto(linhas = 3) }
                    is Carga.Falhou -> item {
                        EstadoDeErro(falhaDe(carga.erro), eventos.tentarDeNovo, mensagem = mensagemDeErro(carga.erro).texto())
                    }
                    is Carga.Pronto -> conteudo(carga.valor, estado, eventos)
                }
            }
        }
    }
    estado.confirmacao?.let { Confirmar(it, eventos) }
}

private fun LazyListScope.conteudo(familia: Familia, estado: EstadoDaFamilia, eventos: EventosDaFamilia) {
    if (estado.conviteDeParceria) item(key = "parceria") { ConviteDeParceria(estado, eventos) }
    item(key = "pessoas") { Pessoas(familia, estado, eventos) }
    item(key = "historico") { Historico(estado, eventos) }
    item(key = "fim") { if (estado.ehTitular) Convidar(familia, estado, eventos) else Sair(estado, eventos) }
}

@Composable
private fun ConviteRecebido(convite: ConviteDaFamilia, estado: EstadoDaFamilia, eventos: EventosDaFamilia) {
    Destaque {
        Text(stringResource(R.string.familia_convite_recebido, convite.familia.orEmpty()), style = MaterialTheme.typography.titleMedium)
        Detalhe(stringResource(R.string.familia_vale_ate, dataCurta(convite.venceEm)))
        Button({ eventos.pedirParaAceitarConvite(convite) }, Modifier.heightIn(min = 48.dp), enabled = !estado.ocupado) {
            Text(stringResource(R.string.familia_entrar_nessa))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConviteDeParceria(estado: EstadoDaFamilia, eventos: EventosDaFamilia) {
    Destaque {
        Text(stringResource(R.string.familia_parceria_titulo), style = MaterialTheme.typography.titleMedium)
        Detalhe(stringResource(R.string.familia_parceria_texto))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(eventos.pedirParaAceitarParceria, Modifier.heightIn(min = 48.dp), enabled = !estado.ocupado) { Text(stringResource(R.string.familia_aceitar)) }
            OutlinedButton(eventos.recusarParceria, Modifier.heightIn(min = 48.dp), enabled = !estado.ocupado) { Text(stringResource(R.string.familia_recusar)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Pessoas(familia: Familia, estado: EstadoDaFamilia, eventos: EventosDaFamilia) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(familia.nome, Modifier.align(Alignment.CenterVertically).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                val plano = stringResource(plano(familia))
                // Só o titular cuida do plano (D-092): para ele a etiqueta abre o Premium.
                if (estado.ehTitular) {
                    val rotulo = stringResource(R.string.familia_plano_abrir, plano)
                    AssistChip(eventos.abrirPremium, { Text(plano) }, Modifier.semantics { contentDescription = rotulo })
                } else {
                    Surface(Modifier.align(Alignment.CenterVertically), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(plano, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (estado.ehTitular) {
                OutlinedTextField(
                    estado.nome,
                    eventos.mudarNome,
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.familia_nome)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { eventos.renomear() }),
                )
                OutlinedButton(eventos.renomear, Modifier.heightIn(min = 48.dp), enabled = !estado.ocupado) { Text(stringResource(R.string.familia_salvar_nome)) }
            }
            familia.membros.forEach { pessoa -> Pessoa(pessoa, estado, eventos) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Pessoa(pessoa: MembroDaFamilia, estado: EstadoDaFamilia, eventos: EventosDaFamilia) {
    val detalhe = listOfNotNull(
        pessoa.email?.takeIf { it.isNotEmpty() },
        stringResource(papel(pessoa.papel)),
        stringResource(R.string.familia_convidado_para_parceiro).takeIf { pessoa.parceriaPendente == true },
    ).joinToString(" · ")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(pessoa.nome, style = MaterialTheme.typography.bodyLarge)
            Detalhe(detalhe)
        }
        if (estado.ehTitular && (pessoa.papel == PigguRole.MEMBRO || pessoa.papel == PigguRole.PARCEIRO)) {
            val papel = when {
                pessoa.parceriaPendente == true -> R.string.familia_cancelar_parceria
                pessoa.papel == PigguRole.PARCEIRO -> R.string.familia_voltar_a_membro
                else -> R.string.familia_convidar_parceiro
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AcaoDaPessoa(stringResource(papel), pessoa.nome, estado.ocupado) { eventos.mudarPapel(pessoa) }
                AcaoDaPessoa(stringResource(R.string.familia_remover), pessoa.nome, estado.ocupado, perigo = true) { eventos.pedirParaRemover(pessoa) }
            }
        }
    }
}

@Composable
private fun AcaoDaPessoa(texto: String, nome: String, ocupado: Boolean, perigo: Boolean = false, aoTocar: () -> Unit) {
    val rotulo = stringResource(R.string.familia_acao_de, texto, nome)
    OutlinedButton(aoTocar, Modifier.heightIn(min = 48.dp).semantics { contentDescription = rotulo }, enabled = !ocupado) {
        Text(texto, color = if (perigo) MaterialTheme.colorScheme.error else Color.Unspecified)
    }
}

@Composable
private fun Historico(estado: EstadoDaFamilia, eventos: EventosDaFamilia) {
    Cartao(stringResource(R.string.familia_historico)) {
        val linhas = estado.historico
        when {
            linhas == null && estado.carregandoHistorico -> Esqueleto(linhas = 3)
            linhas == null -> {
                Detalhe(stringResource(R.string.familia_historico_explica))
                OutlinedButton(eventos.verHistorico, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.familia_ver_historico)) }
            }
            linhas.isEmpty() -> Vazio(Icone.Calendario, stringResource(R.string.familia_historico_vazio), compacto = true)
            else -> linhas.forEach { linha ->
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    Text(descrever(linha), style = MaterialTheme.typography.bodyLarge)
                    linha.mudanca?.let { Detalhe(it) }
                    Detalhe(linha.quando)
                }
            }
        }
    }
}

@Composable
private fun Convidar(familia: Familia, estado: EstadoDaFamilia, eventos: EventosDaFamilia) {
    Cartao(stringResource(R.string.familia_convidar_titulo)) {
        Detalhe(stringResource(R.string.familia_convidar_explica))
        OutlinedTextField(
            estado.email,
            eventos.mudarEmail,
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.familia_email)) },
            placeholder = { Text(stringResource(R.string.familia_email_exemplo)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onSend = { eventos.convidar() }),
        )
        Button(eventos.convidar, Modifier.heightIn(min = 48.dp), enabled = !estado.ocupado) { Text(stringResource(R.string.familia_convidar)) }
        if (familia.convites.isEmpty()) Detalhe(stringResource(R.string.familia_sem_convites))
        familia.convites.forEach { convite ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Text(convite.email, style = MaterialTheme.typography.bodyLarge)
                    Detalhe(stringResource(R.string.familia_vale_ate, dataCurta(convite.venceEm)))
                }
                val rotulo = stringResource(R.string.familia_cancelar_convite_de, convite.email)
                OutlinedButton(
                    { eventos.cancelarConvite(convite) },
                    Modifier.heightIn(min = 48.dp).semantics { contentDescription = rotulo },
                    enabled = !estado.ocupado,
                ) { Text(stringResource(R.string.familia_cancelar)) }
            }
        }
    }
}

@Composable
private fun Sair(estado: EstadoDaFamilia, eventos: EventosDaFamilia) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Detalhe(stringResource(R.string.familia_so_titular))
            OutlinedButton(eventos.pedirParaSair, Modifier.heightIn(min = 48.dp), enabled = !estado.ocupado) {
                Text(stringResource(R.string.familia_sair), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Confirmar(confirmacao: Confirmacao, eventos: EventosDaFamilia) {
    val (titulo, texto, botao) = when (confirmacao) {
        is Confirmacao.AceitarConvite -> Triple(
            stringResource(R.string.familia_confirma_convite_titulo, confirmacao.convite.familia.orEmpty()),
            R.string.familia_confirma_convite, R.string.familia_entrar_nessa,
        )
        Confirmacao.AceitarParceria -> Triple(stringResource(R.string.familia_confirma_parceria_titulo), R.string.familia_confirma_parceria, R.string.familia_aceitar)
        is Confirmacao.ConvidarParceiro -> Triple(
            stringResource(R.string.familia_confirma_convidar_titulo, confirmacao.membro.nome),
            R.string.familia_confirma_convidar, R.string.familia_convidar_parceiro,
        )
        is Confirmacao.VoltarAMembro -> Triple(
            stringResource(R.string.familia_confirma_voltar_titulo, confirmacao.membro.nome),
            R.string.familia_confirma_voltar, R.string.familia_voltar_a_membro,
        )
        is Confirmacao.Remover -> Triple(
            stringResource(R.string.familia_confirma_remover_titulo, confirmacao.membro.nome),
            R.string.familia_confirma_remover, R.string.familia_remover,
        )
        Confirmacao.Sair -> Triple(stringResource(R.string.familia_confirma_sair_titulo), R.string.familia_confirma_sair, R.string.familia_sair)
    }
    val destrutiva = confirmacao is Confirmacao.Remover || confirmacao is Confirmacao.Sair || confirmacao is Confirmacao.AceitarConvite
    AlertDialog(
        onDismissRequest = eventos.desistir,
        confirmButton = {
            TextButton(eventos.confirmar) {
                Text(stringResource(botao), color = if (destrutiva) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(eventos.desistir) { Text(stringResource(Comum.string.cancelar)) } },
        title = { Text(titulo) },
        text = { Text(stringResource(texto)) },
    )
}

@Composable
private fun descrever(linha: LinhaDoHistorico): String {
    val autor = when (val autor = linha.autor) {
        is Autor.Pessoa -> autor.nome
        Autor.Piggu -> stringResource(R.string.familia_piggu)
        Autor.ExMembro -> stringResource(R.string.familia_ex_membro)
    }
    val acao = when (linha.acao) {
        AcaoDeAuditoria.CRIOU -> R.string.familia_acao_criou
        AcaoDeAuditoria.EDITOU, AcaoDeAuditoria.DESCONHECIDO -> R.string.familia_acao_editou
        AcaoDeAuditoria.APAGOU -> R.string.familia_acao_apagou
        AcaoDeAuditoria.IMPORTOU -> R.string.familia_acao_importou
        AcaoDeAuditoria.PAGOU -> R.string.familia_acao_pagou
        AcaoDeAuditoria.MUDOU_PAPEL -> R.string.familia_acao_mudou_papel
        AcaoDeAuditoria.REMOVEU -> R.string.familia_acao_removeu
        AcaoDeAuditoria.ENTROU -> R.string.familia_acao_entrou
        AcaoDeAuditoria.SAIU -> R.string.familia_acao_saiu
        AcaoDeAuditoria.CONVIDOU -> R.string.familia_acao_convidou
    }
    val entidade = when (linha.entidade) {
        "pessoa" -> ""
        "gasto" -> stringResource(R.string.familia_entidade_gasto)
        "receita" -> stringResource(R.string.familia_entidade_receita)
        "meta" -> stringResource(R.string.familia_entidade_meta)
        "orcamento" -> stringResource(R.string.familia_entidade_orcamento)
        "conta-fixa" -> stringResource(R.string.familia_entidade_conta_fixa)
        "fatura" -> stringResource(R.string.familia_entidade_fatura)
        "deposito" -> stringResource(R.string.familia_entidade_deposito)
        else -> linha.entidade
    }
    return listOf(autor, stringResource(acao), entidade).filter { it.isNotEmpty() }.joinToString(" ")
}

private fun plano(familia: Familia): Int = when {
    familia.plano != Plano.PREMIUM -> R.string.familia_plano_gratuito
    familia.planoFamilia -> R.string.familia_plano_familia
    familia.planoCasal == true -> R.string.familia_plano_casal
    else -> R.string.familia_plano_individual
}

private fun papel(papel: PigguRole): Int = when (papel) {
    PigguRole.ADMIN -> R.string.familia_papel_admin
    PigguRole.TITULAR -> R.string.familia_papel_titular
    PigguRole.PARCEIRO -> R.string.familia_papel_parceiro
    PigguRole.MEMBRO, PigguRole.DESCONHECIDO -> R.string.familia_papel_membro
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

/** Convite recebido e convite de parceria: o cartão com a borda de destaque do site. */
@Composable
private fun Destaque(conteudo: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = conteudo)
    }
}

@Composable
private fun Cartao(titulo: String, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titulo, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            conteudo()
        }
    }
}

@Composable
private fun MensagemDeErro.texto(): String = texto(LocalContext.current)

/** Por que a família não carregou, para o ícone e o texto do [EstadoDeErro]. */
internal fun falhaDe(erro: ErroDaApi): FalhaAoCarregar = when {
    erro is ErroDaApi.SemInternet -> FalhaAoCarregar.SemInternet
    erro is ErroDaApi.DoServidor && erro.status >= 500 -> FalhaAoCarregar.DoServidor
    else -> FalhaAoCarregar.Outra
}
