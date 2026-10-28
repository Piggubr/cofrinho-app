package br.com.piggu.feature.calendario

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.model.Nota
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.AvisoDeGuardado
import br.com.piggu.core.ui.CampoDeCategoria
import br.com.piggu.core.ui.CampoDeValor
import br.com.piggu.core.ui.EstadoDeErro
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.FalhaAoCarregar
import br.com.piggu.core.ui.SeletorDeMes
import br.com.piggu.core.ui.Vazio
import br.com.piggu.core.ui.formatos.dataCurta
import br.com.piggu.core.ui.formatos.formatarMoeda
import br.com.piggu.core.ui.formatos.simboloDaMoeda
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import br.com.piggu.core.ui.R as Comum

/** O calendário anda para o futuro (lembretes adiante): o seletor nunca esconde a seta de avançar. */
private const val SEM_LIMITE = "9999-12"

/**
 * Maior escala de fonte da grade: sete colunas no celular, e acima disso o dia de dois dígitos e
 * o valor quebram. O TalkBack lê o dia inteiro (D-095 do iOS).
 */
private const val FONTE_MAXIMA_DA_GRADE = 1.3f

data class EventosDoCalendario(
    val voltar: () -> Unit = {},
    val mudarMes: (String) -> Unit = {},
    val tentarDeNovo: () -> Unit = {},
    val escolherDia: (String) -> Unit = {},
    val apontarNoGrafico: (Int?) -> Unit = {},
    val mudarTitulo: (String) -> Unit = {},
    val mudarTexto: (String) -> Unit = {},
    val mudarComData: (Boolean) -> Unit = {},
    val mudarData: (String) -> Unit = {},
    val mudarValor: (BigDecimal?) -> Unit = {},
    val mudarCategoria: (String) -> Unit = {},
    val criarCategoria: suspend (String) -> Result<List<String>> = { Result.failure(UnsupportedOperationException()) },
    val criarNota: () -> Unit = {},
    val pedirParaApagar: (Nota) -> Unit = {},
    val confirmar: () -> Unit = {},
    val desistir: () -> Unit = {},
)

/** O Calendário em tela cheia, ligado ao [CalendarioViewModel]. */
@Composable
fun TelaDoCalendario(voltar: () -> Unit, modifier: Modifier = Modifier, viewModel: CalendarioViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    TelaDoCalendario(
        estado,
        EventosDoCalendario(
            voltar = voltar,
            mudarMes = viewModel::mudarMes,
            tentarDeNovo = viewModel::tentarDeNovo,
            escolherDia = viewModel::escolherDia,
            apontarNoGrafico = viewModel::apontarNoGrafico,
            mudarTitulo = viewModel::mudarTitulo,
            mudarTexto = viewModel::mudarTexto,
            mudarComData = viewModel::mudarComData,
            mudarData = viewModel::mudarData,
            mudarValor = viewModel::mudarValor,
            mudarCategoria = viewModel::mudarCategoria,
            // O CampoDeCategoria mostra a mensagem da falha.
            criarCategoria = { nome ->
                viewModel.criarCategoria(nome).recoverCatching { falha -> throw IllegalStateException(mensagemDeErro(falha).texto(contexto)) }
            },
            criarNota = viewModel::criarNota,
            pedirParaApagar = viewModel::pedirParaApagar,
            confirmar = viewModel::confirmar,
            desistir = viewModel::desistir,
        ),
        modifier,
    )
}

/**
 * O Calendário só desenhado (capturas e prévias). [ladoALado]: no tablet e no dobrável aberto
 * (janela de largura média ou mais), o mês fica à esquerda e o dia com as notas à direita.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDoCalendario(
    estado: EstadoDoCalendario,
    eventos: EventosDoCalendario,
    modifier: Modifier = Modifier,
    ladoALado: Boolean = currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND),
) {
    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendario_titulo)) },
                navigationIcon = {
                    IconButton(eventos.voltar) { Icon(painterResource(Icone.Esquerda.desenho), stringResource(Comum.string.voltar)) }
                },
            )
        },
    ) { margens ->
        Column(Modifier.padding(margens).fillMaxSize()) {
            SeletorDeMes(estado.mes, SEM_LIMITE, eventos.mudarMes, Modifier.align(Alignment.CenterHorizontally))
            estado.erro?.let { Faixa(it.texto(), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, LiveRegionMode.Assertive) }
            estado.aviso?.let { Faixa(stringResource(it), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, LiveRegionMode.Polite) }
            estado.guardadoEm?.let { AvisoDeGuardado(it, estado.conectado) }
            when (val carga = estado.dados) {
                Carga.Carregando -> Esqueleto(linhas = 5)
                is Carga.Falhou -> EstadoDeErro(falhaDe(carga.erro), eventos.tentarDeNovo, mensagem = mensagemDeErro(carga.erro).texto())
                is Carga.Pronto -> if (ladoALado) {
                    Row(Modifier.fillMaxSize()) {
                        Lista(Modifier.weight(1.2f)) {
                            grade(estado, eventos)
                            grafico(estado, eventos)
                        }
                        Lista(Modifier.weight(1f)) {
                            doDia(estado, eventos, ladoALado = true)
                            novaNota(estado, eventos)
                            semData(estado, eventos)
                        }
                    }
                } else {
                    Lista(Modifier.weight(1f)) {
                        grade(estado, eventos)
                        doDia(estado, eventos, ladoALado = false)
                        grafico(estado, eventos)
                        novaNota(estado, eventos)
                        semData(estado, eventos)
                    }
                }
            }
        }
    }
    estado.apagando?.let { ConfirmarApagar(it, eventos) }
}

// 12dp de cada lado: no celular de 360dp, cada dia da grade fica com os 48dp de toque.
@Composable
private fun Lista(modifier: Modifier, conteudo: LazyListScope.() -> Unit) =
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = conteudo)

@Composable
private fun MensagemDeErro.texto(): String = texto(LocalContext.current)

private fun falhaDe(erro: ErroDaApi): FalhaAoCarregar = when {
    erro is ErroDaApi.SemInternet -> FalhaAoCarregar.SemInternet
    erro is ErroDaApi.DoServidor && erro.status >= 500 -> FalhaAoCarregar.DoServidor
    else -> FalhaAoCarregar.Outra
}

@Composable
private fun Faixa(texto: String, fundo: Color, cor: Color, anuncio: LiveRegionMode) {
    Surface(Modifier.fillMaxWidth(), color = fundo) {
        Text(texto, Modifier.padding(horizontal = 16.dp, vertical = 10.dp).semantics { liveRegion = anuncio }, style = MaterialTheme.typography.bodyMedium, color = cor)
    }
}

@Composable
private fun Cartao(titulo: String?, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            titulo?.let { Text(it, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium) }
            conteudo()
        }
    }
}

// --- O mês ---

private fun LazyListScope.grade(estado: EstadoDoCalendario, eventos: EventosDoCalendario) = item(key = "grade") {
    // A fonte da grade para de crescer em FONTE_MAXIMA_DA_GRADE; o resto da tela segue a do sistema.
    val densidade = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(densidade.density, minOf(densidade.fontScale, FONTE_MAXIMA_DA_GRADE))) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.clearAndSetSemantics {}) {
                stringArrayResource(R.array.calendario_dias_da_semana).forEach { nome ->
                    Text(
                        nome,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            estado.grade.chunked(7).forEach { semana ->
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    for (indice in 0 until 7) {
                        val dia = semana.getOrNull(indice)
                        if (dia == null) {
                            Box(Modifier.weight(1f))
                        } else {
                            Celula(dia, estado.diaEscolhido == dia.iso, estado.moeda, { eventos.escolherDia(dia.iso) }, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** "quarta-feira, 28 de outubro, hoje, gasto de R$ 129,90, 1 lembrete": o que o TalkBack lê do dia. */
@Composable
private fun descricaoDoDia(dia: DiaDaGrade, moeda: String): String = listOfNotNull(
    LocalDate.parse(dia.iso).format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("pt-BR"))),
    if (dia.hoje) stringResource(R.string.calendario_dia_hoje) else null,
    if (dia.gastos.signum() > 0) stringResource(R.string.calendario_dia_gasto, formatarMoeda(dia.gastos, moeda)) else null,
    if (dia.receitas.signum() > 0) stringResource(R.string.calendario_dia_receita, formatarMoeda(dia.receitas, moeda)) else null,
    if (dia.lembretes > 0) pluralStringResource(R.plurals.calendario_dia_lembretes, dia.lembretes, dia.lembretes) else null,
).joinToString(", ")

@Composable
private fun Celula(dia: DiaDaGrade, escolhido: Boolean, moeda: String, aoTocar: () -> Unit, modifier: Modifier) {
    val forma = RoundedCornerShape(10.dp)
    val descricao = descricaoDoDia(dia, moeda)
    Column(
        modifier
            .heightIn(min = 56.dp)
            .background(if (escolhido) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, forma)
            .then(if (dia.hoje) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, forma) else Modifier)
            .clip(forma)
            .selectable(escolhido, role = Role.Button, onClick = aoTocar)
            .semantics { contentDescription = descricao }
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Column(Modifier.clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(dia.dia.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = if (dia.hoje) FontWeight.Bold else null)
            if (dia.gastos.signum() > 0) Valor(compacto(dia.gastos), corDoGasto())
            if (dia.receitas.signum() > 0) Valor(stringResource(R.string.calendario_mais_valor, compacto(dia.receitas)), corDaReceita())
            if (dia.lembretes > 0) Box(Modifier.padding(top = 2.dp).size(5.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}

/** Valor curto na célula: encolhe até caber em vez de quebrar. */
@Composable
private fun Valor(texto: String, cor: Color) {
    BasicText(
        texto,
        style = MaterialTheme.typography.labelSmall.copy(color = cor, fontWeight = FontWeight.Medium),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = MaterialTheme.typography.labelSmall.fontSize),
    )
}

private fun LazyListScope.grafico(estado: EstadoDoCalendario, eventos: EventosDoCalendario) = item(key = "grafico") {
    Cartao(stringResource(R.string.calendario_grafico_titulo)) { GraficoDoMes(estado, eventos.apontarNoGrafico) }
}

// --- O dia e as notas ---

private fun LazyListScope.doDia(estado: EstadoDoCalendario, eventos: EventosDoCalendario, ladoALado: Boolean) {
    val dia = estado.diaEscolhido
    if (dia == null) {
        if (ladoALado) {
            item(key = "dia") {
                Cartao(null) {
                    Text(stringResource(R.string.calendario_toque_num_dia), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }
    item(key = "dia") {
        Cartao(dataCurta(dia)) {
            val gastos = estado.gastosDoDia
            val receitas = estado.receitasDoDia
            val lembretes = estado.lembretesDoDia
            if (gastos.isNotEmpty()) {
                Subtitulo(stringResource(R.string.calendario_gastos))
                gastos.forEach { Linha(it.item, it.categoria, formatarMoeda(it.valor, estado.moeda)) }
            }
            if (receitas.isNotEmpty()) {
                Subtitulo(stringResource(R.string.calendario_receitas))
                receitas.forEach {
                    Linha(it.descricao, it.categoria, stringResource(R.string.calendario_mais_valor, formatarMoeda(it.valor, estado.moeda)), corDaReceita())
                }
            }
            if (lembretes.isNotEmpty()) {
                Subtitulo(stringResource(R.string.calendario_lembretes))
                lembretes.forEach { LinhaDaNota(it, estado.moeda, eventos) }
            }
            if (gastos.isEmpty() && receitas.isEmpty() && lembretes.isEmpty()) {
                Text(stringResource(R.string.calendario_nada_no_dia), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Subtitulo(texto: String) =
    Text(texto, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)

/** Título e detalhe à esquerda, o valor à direita, lidos juntos; as ações ficam fora. */
@Composable
private fun Linha(titulo: String, detalhe: String?, valor: String?, corDoValor: Color = MaterialTheme.colorScheme.onSurface, acoes: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.bodyLarge)
                if (!detalhe.isNullOrEmpty()) Text(detalhe, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            valor?.let { Text(it, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = corDoValor) }
        }
        acoes()
    }
}

@Composable
private fun LinhaDaNota(nota: Nota, moeda: String, eventos: EventosDoCalendario) {
    val valor = nota.valor?.takeIf { it.signum() > 0 }?.let { formatarMoeda(it, moeda) }
    Linha(nota.titulo, nota.texto, valor) {
        IconButton({ eventos.pedirParaApagar(nota) }) {
            Icon(painterResource(Icone.Lixeira.desenho), stringResource(R.string.calendario_apagar_nota, nota.titulo), tint = MaterialTheme.colorScheme.error)
        }
    }
}

private fun LazyListScope.semData(estado: EstadoDoCalendario, eventos: EventosDoCalendario) = item(key = "sem-data") {
    Cartao(stringResource(R.string.calendario_sem_data)) {
        val notas = estado.semData
        if (notas.isEmpty()) {
            Vazio(Icone.Nota, stringResource(R.string.calendario_nenhuma_nota_solta), compacto = true)
        }
        notas.forEach { LinhaDaNota(it, estado.moeda, eventos) }
    }
}

private fun LazyListScope.novaNota(estado: EstadoDoCalendario, eventos: EventosDoCalendario) = item(key = "nova-nota") {
    val nota = estado.nova
    Cartao(stringResource(R.string.calendario_nova_nota)) {
        OutlinedTextField(
            nota.titulo,
            eventos.mudarTitulo,
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.calendario_nota_titulo)) },
            singleLine = true,
            isError = estado.faltouTitulo,
        )
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(nota.comData, role = Role.Switch, onValueChange = eventos.mudarComData),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.calendario_nota_com_data), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(nota.comData, onCheckedChange = null)
        }
        if (nota.comData) CampoDeData(nota.data, eventos.mudarData)
        CampoDeValor(
            nota.valor,
            eventos.mudarValor,
            estado.moeda,
            Modifier.fillMaxWidth(),
            rotulo = stringResource(R.string.calendario_nota_valor, simboloDaMoeda(estado.moeda)),
        )
        Text(stringResource(R.string.calendario_nota_valor_dica), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (nota.viraGasto) {
            CampoDeCategoria(estado.categorias, nota.categoria, eventos.mudarCategoria, eventos.criarCategoria, {}, Modifier.fillMaxWidth(), automatica = false)
        }
        OutlinedTextField(
            nota.texto,
            eventos.mudarTexto,
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.calendario_nota_detalhes)) },
            minLines = 2,
            maxLines = 6,
        )
        if (nota.viraGasto) {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(8.dp)) {
                Text(
                    stringResource(R.string.calendario_vira_gasto),
                    Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        Button(eventos.criarNota, Modifier.fillMaxWidth(), enabled = !estado.guardando) {
            Text(stringResource(if (estado.guardando) R.string.calendario_guardando else R.string.calendario_criar_nota))
        }
    }
}

/**
 * Data (aaaa-mm-dd) num campo só de leitura que abre o calendário. O DatePicker trabalha em
 * milissegundos UTC: por isso a conversão por UTC, para não deslocar o dia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampoDeData(data: String, aoMudar: (String) -> Unit) {
    var aberto by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        dataCurta(data),
        {},
        Modifier.fillMaxWidth(),
        readOnly = true,
        label = { Text(stringResource(R.string.calendario_nota_data)) },
        singleLine = true,
        trailingIcon = {
            IconButton({ aberto = true }) { Icon(painterResource(Icone.Calendario.desenho), stringResource(R.string.calendario_escolher_data)) }
        },
    )
    if (!aberto) return
    val dia = runCatching { LocalDate.parse(data) }.getOrNull()
    val calendario = rememberDatePickerState(dia?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
    DatePickerDialog(
        onDismissRequest = { aberto = false },
        confirmButton = {
            TextButton({
                calendario.selectedDateMillis?.let { aoMudar(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                aberto = false
            }) { Text(stringResource(R.string.calendario_ok)) }
        },
        dismissButton = { TextButton({ aberto = false }) { Text(stringResource(Comum.string.cancelar)) } },
    ) { DatePicker(calendario) }
}

/** Confirma antes de apagar, como o site; a nota com gasto avisa que o gasto vai junto. */
@Composable
private fun ConfirmarApagar(nota: Nota, eventos: EventosDoCalendario) {
    AlertDialog(
        onDismissRequest = eventos.desistir,
        confirmButton = {
            TextButton(eventos.confirmar) { Text(stringResource(R.string.calendario_apagar), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(eventos.desistir) { Text(stringResource(Comum.string.cancelar)) } },
        title = { Text(stringResource(R.string.calendario_apagar_pergunta, nota.titulo)) },
        text = if (nota.gastoId != null) {
            { Text(stringResource(R.string.calendario_apagar_gasto_junto)) }
        } else {
            null
        },
    )
}
