package br.com.piggu.feature.painel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.model.AlertaDoOrcamento
import br.com.piggu.core.model.ResumoDoMes
import br.com.piggu.core.model.SituacaoDaContaFixa
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.ui.AnelDeProgresso
import br.com.piggu.core.ui.BarraDeProgresso
import br.com.piggu.core.ui.CampoDeValor
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.Vazio
import br.com.piggu.core.ui.formatos.dataCurta
import br.com.piggu.core.ui.formatos.formatarMoeda
import br.com.piggu.core.ui.formatos.mesPorExtenso
import br.com.piggu.core.ui.formatos.simboloDaMoeda
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Currency
import java.util.Locale

// Os cartões do Painel, um para cada bloco de frontend/src/app/features/dashboard, com os mesmos
// textos e as mesmas regras de mostrar e esconder (CartoesDoPainel.swift do iOS).

private val PtBr = Locale.forLanguageTag("pt-BR")

@Composable
private fun CartaoBase(
    titulo: String?,
    modifier: Modifier = Modifier,
    extra: @Composable RowScope.() -> Unit = {},
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (titulo != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(titulo, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    extra()
                }
            }
            conteudo()
        }
    }
}

@Composable
private fun Etiqueta(texto: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(texto, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

/** O erro de uma ação ou de carregar o cartão; o TalkBack lê quando aparece. */
@Composable
private fun ErroDoCartao(erro: MensagemDeErro?) {
    erro ?: return
    Text(
        erro.texto(LocalContext.current),
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun Detalhe(texto: String, modifier: Modifier = Modifier, cor: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Text(texto, modifier, style = MaterialTheme.typography.bodyMedium, color = cor)

@Composable
private fun Numero(rotulo: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Detalhe(rotulo)
        Text(valor, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BotaoApagar(aoApagar: () -> Unit) {
    IconButton(aoApagar) {
        Icon(painterResource(Icone.Lixeira.desenho), stringResource(R.string.apagar), tint = MaterialTheme.colorScheme.error)
    }
}

// Resumo do mês (resumo-do-mes.ts)

@Composable
internal fun CartaoDoResumo(resumo: ResumoDoMes, moeda: String?, abrirTela: (String) -> Unit) {
    val cores = MaterialTheme.colorScheme
    val texto = cores.onPrimary
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(cores.primary, cores.tertiary)), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.resumo_titulo), Modifier.weight(1f).semantics { heading() }, color = texto, style = MaterialTheme.typography.titleSmall)
            resumo.taxaDePoupanca?.let { taxa ->
                Surface(shape = CircleShape, color = texto.copy(alpha = 0.18f)) {
                    Text(
                        stringResource(R.string.resumo_poupado, taxa.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')),
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = texto,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Text(formatarMoeda(resumo.sobra, moeda), color = texto, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        BoxWithConstraints {
            // Lado a lado só com uns 150dp para cada na fonte do aparelho; senão o valor quebraria no meio.
            val ladoALado = maxWidth / LocalDensity.current.fontScale >= 300.dp
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = if (ladoALado) 2 else 1,
            ) {
                Parcela(stringResource(R.string.resumo_receitas), formatarMoeda(resumo.receitas, moeda), Icone.Entrada, texto, Modifier.weight(1f))
                Parcela(stringResource(R.string.resumo_gastos), formatarMoeda(resumo.gastos, moeda), Icone.Carteira, texto, Modifier.weight(1f))
            }
        }
        if (resumo.receitas.signum() == 0) {
            TextButton({ abrirTela("/receitas") }) { Text(stringResource(R.string.resumo_dica), color = texto) }
        }
    }
}

@Composable
private fun Parcela(rotulo: String, valor: String, icone: Icone, cor: Color, modifier: Modifier) {
    Row(
        modifier
            .background(cor.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(icone.desenho), null, Modifier.size(18.dp), tint = cor)
        Column {
            Text(rotulo, color = cor, style = MaterialTheme.typography.labelMedium)
            Text(valor, color = cor, style = MaterialTheme.typography.titleSmall)
        }
    }
}

// Para ficar de olho (avisos-do-mes.ts)

@Composable
internal fun CartaoDeAvisos(avisos: AvisosDoMes, moeda: String?, abrirTela: (String) -> Unit) {
    if (avisos.vazio) return
    CartaoBase(stringResource(R.string.bloco_avisos)) {
        avisos.orcamentos.forEach { orcamento ->
            val estourou = orcamento.alerta == AlertaDoOrcamento.ESTOUROU
            LinhaDeAviso(
                Icone.Pizza,
                stringResource(R.string.aviso_orcamento),
                if (estourou) {
                    stringResource(R.string.aviso_orcamento_estourou, orcamento.categoria, formatarMoeda(orcamento.gasto, moeda), formatarMoeda(orcamento.limite, moeda))
                } else {
                    stringResource(R.string.aviso_orcamento_atencao, orcamento.categoria, orcamento.percentual.setScale(0, RoundingMode.HALF_UP).toPlainString())
                },
                estourou,
            ) { abrirTela("/orcamentos") }
        }
        avisos.faturas.forEach { cartao ->
            val fatura = cartao.faturaAPagar ?: return@forEach
            LinhaDeAviso(
                Icone.Cartao,
                stringResource(R.string.aviso_fatura),
                stringResource(R.string.aviso_fatura_vence, cartao.nome, dataCurta(fatura.vencimento), formatarMoeda(fatura.total, moeda)),
                false,
            ) { abrirTela("/contas-e-cartoes") }
        }
        avisos.contas.forEach { conta ->
            val venceu = conta.situacao == SituacaoDaContaFixa.VENCIDA
            LinhaDeAviso(
                Icone.Recibo,
                stringResource(R.string.data_e_pessoa, conta.descricao, formatarMoeda(conta.valor, moeda)),
                stringResource(if (venceu) R.string.aviso_venceu else R.string.aviso_vence, dataCurta(conta.vencimento)),
                venceu,
            ) { abrirTela("/contas-fixas") }
        }
        avisos.devendo.forEach { conta ->
            LinhaDeAviso(
                Icone.Entrada,
                stringResource(R.string.data_e_pessoa, conta.quem, formatarMoeda(conta.valor, moeda)),
                stringResource(R.string.aviso_devendo, dataCurta(conta.data)),
                true,
            ) { abrirTela("/receitas") }
        }
    }
}

@Composable
private fun LinhaDeAviso(icone: Icone, titulo: String, detalhe: String, atrasada: Boolean, abrir: () -> Unit) {
    val cores = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = abrir).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(38.dp).background(if (atrasada) cores.errorContainer else cores.primaryContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icone.desenho), null, Modifier.size(18.dp), tint = if (atrasada) cores.error else cores.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Detalhe(detalhe, cor = if (atrasada) cores.error else cores.onSurfaceVariant)
        }
        Icon(painterResource(Icone.Direita.desenho), null, Modifier.size(18.dp), tint = cores.onSurfaceVariant)
    }
}

// Gasto e limite do mês

@Composable
internal fun CartaoDoMes(estado: EstadoDoPainel, abrirTela: (String) -> Unit) {
    val temLimite = estado.limiteDoMes.signum() > 0
    CartaoBase(mesPorExtenso(estado.mes)) {
        Row {
            Numero(stringResource(R.string.mes_gasto), formatarMoeda(estado.gastoNoMes, estado.moeda), Modifier.weight(1f))
            Numero(stringResource(R.string.mes_limite), if (temLimite) formatarMoeda(estado.limiteDoMes, estado.moeda) else "—", Modifier.weight(1f))
        }
        if (temLimite) {
            val cor = if (estado.estourouOLimite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            BarraDeProgresso(estado.progressoDoLimite / 100f, cor = cor)
            Detalhe(
                stringResource(if (estado.estourouOLimite) R.string.mes_estourou else R.string.mes_progresso, estado.progressoDoLimite),
                cor = if (estado.estourouOLimite) cor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Detalhe(stringResource(R.string.mes_sem_limite))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ abrirTela("/orcamentos") }) { Text(stringResource(R.string.mes_definir_limite)) }
            OutlinedButton({ abrirTela("/gastos") }) { Text(stringResource(R.string.mes_ver_gastos)) }
        }
    }
}

// Por categoria

@Composable
internal fun CartaoDasCategorias(estado: EstadoDoPainel, lancarGasto: () -> Unit) {
    CartaoBase(stringResource(R.string.bloco_categorias)) {
        val totais = estado.porCategoria
        if (totais.isEmpty()) {
            Vazio(Icone.Recibo, stringResource(R.string.categorias_vazio), compacto = true, acao = stringResource(R.string.lancar_um_gasto) to lancarGasto)
        }
        totais.forEach { linha ->
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(linha.categoria, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(formatarMoeda(linha.total, estado.moeda), style = MaterialTheme.typography.bodyLarge)
                }
                BarraDeProgresso(linha.fracao)
            }
        }
    }
}

// Cofrinho

@Composable
internal fun CartaoDoCofrinho(estado: EstadoDoPainel, eventos: EventosDoPainel, perguntar: (Pergunta) -> Unit) {
    val cofrinho = (estado.carga as? Carga.Pronto)?.valor?.cofrinho ?: return
    val foco = remember { FocusRequester() }
    val salvando = Acao.Deposito in estado.ocupado
    val perguntaApagar = stringResource(R.string.cofrinho_apagar_pergunta)
    val apagar = stringResource(R.string.apagar)
    CartaoBase(stringResource(R.string.bloco_cofrinho), extra = { Etiqueta(formatarMoeda(cofrinho.saldo, estado.moeda)) }) {
        Row {
            Numero(stringResource(R.string.cofrinho_depositado), formatarMoeda(cofrinho.totalDepositos, estado.moeda), Modifier.weight(1f))
            Numero(stringResource(R.string.cofrinho_gasto), formatarMoeda(cofrinho.totalGastos, estado.moeda), Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoDeValor(
                estado.deposito,
                eventos.mudarDeposito,
                estado.moeda ?: "BRL",
                Modifier.weight(1f).focusRequester(foco),
                rotulo = stringResource(R.string.cofrinho_novo_deposito, simboloDaMoeda(estado.moeda ?: "BRL")),
            )
            Button(eventos.depositar, enabled = !salvando) {
                Text(stringResource(if (salvando) R.string.cofrinho_guardando else R.string.cofrinho_depositar))
            }
        }
        ErroDoCartao(estado.erros[IdDoBloco.Cofrinho])
        if (cofrinho.depositos.isEmpty()) {
            Vazio(Icone.Cofrinho, stringResource(R.string.cofrinho_vazio), compacto = true, acao = stringResource(R.string.cofrinho_primeiro) to { foco.requestFocus() })
        }
        cofrinho.depositos.forEach { deposito ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Text(formatarMoeda(deposito.valor, estado.moeda), style = MaterialTheme.typography.bodyLarge)
                    Detalhe(stringResource(R.string.data_e_pessoa, dataCurta(deposito.data), estado.nome(deposito.usuario) ?: stringResource(R.string.ex_membro)))
                }
                BotaoApagar { perguntar(Pergunta(perguntaApagar, apagar) { eventos.excluirDeposito(deposito.id) }) }
            }
        }
    }
}

// Cofrinhos por objetivo (objetivos.ts)

@Composable
internal fun CartaoDosObjetivos(estado: EstadoDoPainel, eventos: EventosDoPainel, abrirTela: (String) -> Unit, perguntar: (Pergunta) -> Unit) {
    val apagar = stringResource(R.string.apagar)
    val recursos = LocalResources.current
    CartaoBase(stringResource(R.string.bloco_objetivos)) {
        ErroDoCartao(estado.erros[IdDoBloco.Objetivos])
        val objetivos = estado.objetivos
        if (objetivos == null) {
            Esqueleto(linhas = 2)
            return@CartaoBase
        }
        if (objetivos.isEmpty()) Detalhe(stringResource(R.string.objetivos_vazio))
        objetivos.forEach { objetivo ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(objetivo.nome, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            if (objetivo.compartilhado == true) Etiqueta(stringResource(R.string.objetivo_a_dois))
                        }
                        Detalhe(stringResource(R.string.objetivo_guardado, formatarMoeda(objetivo.guardado, estado.moeda), formatarMoeda(objetivo.alvo, estado.moeda)))
                        if (!estado.ehMeu(objetivo.usuario) && estado.moraComAlguem) {
                            Detalhe(stringResource(R.string.objetivo_criado_por, estado.nome(objetivo.usuario) ?: stringResource(R.string.ex_membro)))
                        }
                    }
                    IconButton({ eventos.editarObjetivo(objetivo) }) { Icon(painterResource(Icone.Lapis.desenho), stringResource(R.string.editar)) }
                    // Compartilhado é a dois: os dois editam, só quem criou apaga.
                    if (estado.ehMeu(objetivo.usuario)) {
                        BotaoApagar {
                            perguntar(Pergunta(recursos.getString(R.string.objetivo_apagar_pergunta, objetivo.nome), apagar) { eventos.excluirObjetivo(objetivo.id) })
                        }
                    }
                }
                BarraDeProgresso(objetivo.percentual / 100f, cor = if (objetivo.concluido) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                Detalhe(
                    when {
                        objetivo.concluido -> stringResource(R.string.objetivo_alcancado)
                        objetivo.atrasado -> stringResource(R.string.objetivo_atrasado, formatarMoeda(objetivo.falta, estado.moeda))
                        objetivo.porMes != null && objetivo.prazo != null ->
                            stringResource(R.string.objetivo_por_mes, formatarMoeda(objetivo.porMes, estado.moeda), dataCurta(objetivo.prazo))
                        else -> stringResource(R.string.objetivo_sem_prazo, formatarMoeda(objetivo.falta, estado.moeda))
                    },
                )
                if (!objetivo.concluido) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CampoDeValor(
                            estado.valoresDosObjetivos[objetivo.id],
                            { eventos.mudarValorDoObjetivo(objetivo.id, it) },
                            estado.moeda ?: "BRL",
                            Modifier.weight(1f),
                            rotulo = stringResource(R.string.objetivo_guardar_em, objetivo.nome, simboloDaMoeda(estado.moeda ?: "BRL")),
                        )
                        OutlinedButton({ eventos.depositarNoObjetivo(objetivo.id) }, enabled = Acao.Objetivo !in estado.ocupado) {
                            Text(stringResource(R.string.objetivo_guardar))
                        }
                    }
                }
                HorizontalDivider()
            }
        }
        val formulario = estado.formulario
        when {
            formulario != null -> FormularioDoObjetivo(formulario, estado, eventos)
            estado.podeCriarObjetivo -> OutlinedButton(eventos.novoObjetivo) {
                Text(stringResource(if (objetivos.isEmpty()) R.string.objetivo_primeiro else R.string.objetivo_novo))
            }
            else -> {
                Detalhe(stringResource(R.string.objetivo_convite))
                OutlinedButton({ abrirTela("/plano") }) { Text(stringResource(R.string.objetivo_conhecer_premium)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormularioDoObjetivo(formulario: FormularioDeObjetivo, estado: EstadoDoPainel, eventos: EventosDoPainel) {
    var escolhendoData by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(if (formulario.id == null) R.string.objetivo_novo else R.string.objetivo_editar),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
        )
        OutlinedTextField(
            formulario.nome,
            { eventos.mudarFormulario(formulario.copy(nome = it)) },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.objetivo_nome)) },
            placeholder = { Text(stringResource(R.string.objetivo_nome_exemplo)) },
            singleLine = true,
        )
        CampoDeValor(
            formulario.alvo,
            { eventos.mudarFormulario(formulario.copy(alvo = it)) },
            estado.moeda ?: "BRL",
            Modifier.fillMaxWidth(),
            rotulo = stringResource(R.string.objetivo_quanto, simboloDaMoeda(estado.moeda ?: "BRL")),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ escolhendoData = true }) {
                Text(formulario.prazo?.let { stringResource(R.string.objetivo_prazo, dataCurta(it)) } ?: stringResource(R.string.objetivo_ate_quando))
            }
            if (formulario.prazo != null) {
                TextButton({ eventos.mudarFormulario(formulario.copy(prazo = null)) }) { Text(stringResource(R.string.objetivo_tirar_prazo)) }
            }
        }
        if (estado.podeCompartilhar) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(formulario.compartilhado, role = Role.Checkbox) { eventos.mudarFormulario(formulario.copy(compartilhado = it)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(formulario.compartilhado, null)
                Text(stringResource(R.string.objetivo_compartilhado), Modifier.padding(start = 8.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(eventos.salvarObjetivo, enabled = Acao.Objetivo !in estado.ocupado) { Text(stringResource(R.string.objetivo_salvar)) }
            TextButton(eventos.fecharFormulario) { Text(stringResource(R.string.cancelar)) }
        }
    }
    if (escolhendoData) {
        // O DatePicker trabalha em milissegundos UTC; a API, com o dia civil.
        val hoje = LocalDate.parse(estado.hoje).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val data = rememberDatePickerState(
            initialSelectedDateMillis = formulario.prazo?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= hoje
            },
        )
        DatePickerDialog(
            onDismissRequest = { escolhendoData = false },
            confirmButton = {
                TextButton({
                    data.selectedDateMillis?.let { eventos.mudarFormulario(formulario.copy(prazo = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())) }
                    escolhendoData = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton({ escolhendoData = false }) { Text(stringResource(R.string.cancelar)) } },
        ) { DatePicker(data) }
    }
}

// Minhas metas (metas-do-painel.ts)

@Composable
internal fun CartaoDasMetas(estado: EstadoDoPainel, abrirTela: (String) -> Unit) {
    CartaoBase(null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            AnelDeProgresso(estado.percentualDasMetas / 100f) {
                Text(stringResource(R.string.metas_percentual, estado.percentualDasMetas), style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.metas_ritmo).uppercase(PtBr), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.metas_titulo), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                val metas = estado.metasDoMes.orEmpty()
                val erro = estado.erros[IdDoBloco.Metas]
                when {
                    erro != null -> ErroDoCartao(erro)
                    metas.isNotEmpty() -> Detalhe(pluralStringResource(R.plurals.metas_concluidas, metas.size, estado.metasConcluidas, metas.size))
                    estado.metasDoMes != null -> Detalhe(stringResource(R.string.metas_vazio))
                }
                OutlinedButton({ abrirTela("/metas") }) { Text(stringResource(R.string.metas_ver)) }
            }
        }
    }
}

// Girls Math (girls-math-do-painel.ts)

@Composable
internal fun CartaoDoGirlsMath(estado: EstadoDoPainel, abrirTela: (String) -> Unit) {
    CartaoBase(
        stringResource(R.string.bloco_girls_math),
        extra = { Icon(painterResource(Icone.Sacola.desenho), null, tint = MaterialTheme.colorScheme.primary) },
    ) {
        val erro = estado.erros[IdDoBloco.GirlsMath]
        if (erro != null) {
            ErroDoCartao(erro)
        } else {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Detalhe(stringResource(R.string.girls_math_texto))
                Text(
                    formatarMoeda(estado.girlsMathNoMes, estado.moeda),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        OutlinedButton({ abrirTela("/girls-math") }) { Text(stringResource(R.string.girls_math_registrar)) }
    }
}

// Suas notas (notas-do-painel.ts): nota rápida, sem data nem valor.

@Composable
internal fun CartaoDasNotas(estado: EstadoDoPainel, eventos: EventosDoPainel, abrirTela: (String) -> Unit, perguntar: (Pergunta) -> Unit) {
    val apagar = stringResource(R.string.apagar)
    val recursos = LocalResources.current
    CartaoBase(stringResource(R.string.bloco_notas), extra = {
        TextButton({ abrirTela("/calendario") }) { Text(stringResource(R.string.notas_ver_todas)) }
    }) {
        ErroDoCartao(estado.erros[IdDoBloco.Notas])
        estado.notasRecentes.forEach { nota ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Text(nota.titulo, style = MaterialTheme.typography.bodyLarge)
                    val detalhe = listOfNotNull(nota.data?.let(::dataCurta), nota.texto).filter { it.isNotEmpty() }.joinToString(" · ")
                    if (detalhe.isNotEmpty()) Detalhe(detalhe)
                }
                BotaoApagar { perguntar(Pergunta(recursos.getString(R.string.nota_apagar_pergunta, nota.titulo), apagar) { eventos.excluirNota(nota.id) }) }
            }
        }
        if (estado.notas?.isEmpty() == true) Detalhe(stringResource(R.string.notas_vazio))
        val nova = estado.notaNova
        if (nova == null) {
            OutlinedButton(eventos.escreverNota) { Text(stringResource(R.string.notas_criar)) }
        } else {
            OutlinedTextField(
                nova.titulo,
                { eventos.mudarNota(nova.copy(titulo = it)) },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.notas_titulo_campo)) },
                singleLine = true,
            )
            OutlinedTextField(
                nova.texto,
                { eventos.mudarNota(nova.copy(texto = it)) },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.notas_texto_campo)) },
                placeholder = { Text(stringResource(R.string.notas_texto_exemplo)) },
                minLines = 3,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val salvando = Acao.Nota in estado.ocupado
                Button(eventos.salvarNota, enabled = !salvando) {
                    Text(stringResource(if (salvando) R.string.cofrinho_guardando else R.string.notas_salvar))
                }
                TextButton(eventos.cancelarNota) { Text(stringResource(R.string.cancelar)) }
            }
        }
    }
}

// Contas bancárias (Open Finance): só com o status habilitado.

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CartaoDosBancos(estado: EstadoDoPainel, eventos: EventosDoPainel, abrirTela: (String) -> Unit, perguntar: (Pergunta) -> Unit) {
    val contas = estado.bancos ?: return
    val ocupado = Acao.Bancos in estado.ocupado
    val recursos = LocalResources.current
    val desconectar = stringResource(R.string.bancos_desconectar)
    CartaoBase(stringResource(R.string.bancos_titulo), extra = {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            estado.saldoPorMoeda.forEach { (moeda, total) -> Etiqueta(formatarMoeda(total, moeda)) }
        }
    }) {
        ErroDoCartao(estado.erroDosBancos)
        if (contas.isEmpty()) Detalhe(stringResource(R.string.bancos_vazio))
        contas.forEach { conta ->
            Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(conta.nome, style = MaterialTheme.typography.bodyLarge)
                    Detalhe(listOf(conta.instituicao, conta.numero.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · "))
                }
                Text(formatarMoeda(conta.saldo, conta.moeda), style = MaterialTheme.typography.bodyLarge)
            }
        }
        estado.conexoes.forEach { (conexaoId, instituicao) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Detalhe(stringResource(R.string.bancos_conectado, instituicao), Modifier.weight(1f))
                OutlinedButton(
                    { perguntar(Pergunta(recursos.getString(R.string.bancos_desconectar_pergunta, instituicao), desconectar) { eventos.desconectarBanco(conexaoId) }) },
                    enabled = !ocupado,
                ) { Text(desconectar) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!estado.ehPremium) {
                OutlinedButton({ abrirTela("/plano") }) { Text(stringResource(R.string.bancos_conectar_premium)) }
            } else {
                // A conexão passa pelo widget da Pluggy, que vive no site.
                OutlinedButton({ abrirTela("/painel") }, enabled = !ocupado) {
                    Text(stringResource(if (ocupado) R.string.bancos_aguarde else R.string.bancos_conectar))
                }
                if (contas.isNotEmpty()) {
                    TextButton(eventos.sincronizarBancos, enabled = !ocupado) { Text(stringResource(R.string.bancos_atualizar)) }
                }
            }
        }
    }
}

// Primeiros passos (primeiros-passos.ts)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CartaoDoGuia(estado: EstadoDoPainel, eventos: EventosDoPainel, abrirTela: (String) -> Unit, lancarGasto: () -> Unit, modifier: Modifier) {
    CartaoBase(stringResource(R.string.guia_titulo), modifier, extra = { Etiqueta(stringResource(R.string.guia_feitos, estado.passosFeitos)) }) {
        Passo(stringResource(R.string.guia_moeda), estado.moedaFeita) {
            if (estado.moedaFeita) {
                Detalhe(nomeDaMoeda(estado.moeda ?: "BRL"))
            } else {
                val escolhida = estado.moedaEscolhida ?: estado.moeda
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    estado.moedasDoGuia.forEach { codigo ->
                        FilterChip(codigo == escolhida, { eventos.escolherMoeda(codigo) }, { Text(nomeDaMoeda(codigo)) })
                    }
                }
                Button(eventos.usarMoeda, enabled = Acao.Moeda !in estado.ocupado && escolhida != null) { Text(stringResource(R.string.guia_usar_esta)) }
            }
        }
        Passo(stringResource(R.string.guia_limite), estado.temLimite) {
            if (!estado.temLimite) OutlinedButton({ abrirTela("/orcamentos") }) { Text(stringResource(R.string.guia_definir_limite)) }
        }
        Passo(stringResource(R.string.guia_gasto), estado.temGasto) {
            if (!estado.temGasto) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(lancarGasto) { Text(stringResource(R.string.guia_lancar)) }
                    OutlinedButton({ abrirTela("/gastos#importar") }) { Text(stringResource(R.string.guia_importar)) }
                }
            }
        }
        ErroDoCartao(estado.erros[IdDoBloco.Resumo])
        TextButton(eventos.pularGuia) { Text(stringResource(R.string.guia_pular)) }
    }
}

@Composable
private fun Passo(titulo: String, feito: Boolean, conteudo: @Composable ColumnScope.() -> Unit) {
    val feitoTexto = stringResource(R.string.guia_feito)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            titulo,
            Modifier.semantics { if (feito) stateDescription = feitoTexto },
            style = MaterialTheme.typography.bodyLarge,
            color = if (feito) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (feito) TextDecoration.LineThrough else null,
        )
        conteudo()
    }
}

/** "Real brasileiro (BRL)", como o Intl.DisplayNames do site. */
private fun nomeDaMoeda(codigo: String): String =
    runCatching { "${Currency.getInstance(codigo).getDisplayName(PtBr).replaceFirstChar { it.titlecase(PtBr) }} ($codigo)" }.getOrDefault(codigo)
