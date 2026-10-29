package br.com.piggu.feature.login.cadastro

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.piggu.core.designsystem.R as Icones
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.ui.HumorDoPorquinho
import br.com.piggu.core.ui.Porquinho
import br.com.piggu.core.ui.R as Comum
import br.com.piggu.core.ui.formatos.EXEMPLO_DO_CELULAR
import br.com.piggu.core.ui.formatos.EXEMPLO_DO_CPF
import br.com.piggu.core.ui.formatos.dataCurta
import br.com.piggu.core.ui.formatos.ddiDoPais
import br.com.piggu.core.ui.formatos.nomeDoPais
import br.com.piggu.core.ui.formatos.paises
import br.com.piggu.feature.login.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Peças das telas de entrada (Sobre você, CPF e o formulário de cadastro), como o
// ComponentesDaEntrada.swift do iOS.

/** O cartão das telas de entrada do site (.tela-de-login): porquinho, título, frase e o formulário. */
@Composable
internal fun MolduraDaEntrada(titulo: String, subtitulo: String, conteudo: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Porquinho(HumorDoPorquinho.Feliz, largura = 120.dp)
            Text(
                titulo,
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(subtitulo, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                conteudo()
            }
        }
    }
}

/** Erro sem campo (sem internet, servidor), lido pelo leitor de tela quando aparece. */
@Composable
internal fun ErroNoTopo(erro: MensagemDeErro?) {
    erro ?: return
    Text(
        erro.texto(LocalContext.current),
        Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        color = MaterialTheme.colorScheme.error,
    )
}

/** A barra de "enviando", com o texto para o leitor de tela. */
@Composable
internal fun Enviando(enviando: Boolean) {
    if (!enviando) return
    Text(stringResource(R.string.login_confirmando), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    LinearProgressIndicator(Modifier.fillMaxWidth())
}

/** A mensagem de um campo, embaixo dele, ou a dica quando não há erro. */
@Composable
internal fun textoDeApoio(erro: MensagemDeErro?, dica: String? = null): (@Composable () -> Unit)? = when {
    erro != null -> {
        val texto = erro.texto(LocalContext.current)
        ({ Text(texto) })
    }
    dica != null -> ({ Text(dica) })
    else -> null
}

/** Os países pelo nome, com o Brasil primeiro. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeletorDePais(pais: String, aoEscolher: (String) -> Unit, modifier: Modifier = Modifier) {
    var aberto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(aberto, { aberto = it }, modifier) {
        OutlinedTextField(
            nomeDoPais(pais),
            {},
            Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            label = { Text(stringResource(R.string.cadastro_pais)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(aberto) },
        )
        ExposedDropdownMenu(aberto, { aberto = false }) {
            paises.forEach { opcao ->
                DropdownMenuItem({ Text(opcao.nome) }, { aoEscolher(opcao.codigo); aberto = false })
            }
        }
    }
}

/** DDI e número, como no cadastro do site; o backend junta os dois. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CampoDeCelular(
    paisDoDdi: String,
    aoEscolherDdi: (String) -> Unit,
    telefone: String,
    aoDigitar: (String) -> Unit,
    erro: MensagemDeErro?,
    foco: FocusRequester,
) {
    var aberto by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(aberto, { aberto = it }, Modifier.widthIn(max = 128.dp)) {
            OutlinedTextField(
                "+${ddiDoPais(paisDoDdi)}",
                {},
                Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                readOnly = true,
                label = { Text(stringResource(R.string.cadastro_ddi)) },
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(aberto) },
            )
            ExposedDropdownMenu(aberto, { aberto = false }) {
                paises.forEach { opcao ->
                    DropdownMenuItem(
                        { Text(stringResource(R.string.cadastro_ddi_do_pais, opcao.nome, ddiDoPais(opcao.codigo))) },
                        { aoEscolherDdi(opcao.codigo); aberto = false },
                    )
                }
            }
        }
        OutlinedTextField(
            telefone,
            aoDigitar,
            Modifier.weight(1f).focusRequester(foco).semantics { contentType = ContentType.PhoneNumberNational },
            label = { Text(stringResource(R.string.cadastro_celular)) },
            placeholder = { Text(EXEMPLO_DO_CELULAR) },
            singleLine = true,
            isError = erro != null,
            supportingText = textoDeApoio(erro),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
        )
    }
}

/** CPF com a máscara enquanto digita (000.000.000-00); quem formata é o ViewModel. */
@Composable
internal fun CampoDeCpf(
    documento: String,
    aoDigitar: (String) -> Unit,
    erro: MensagemDeErro?,
    foco: FocusRequester,
    dica: String? = null,
) {
    OutlinedTextField(
        documento,
        aoDigitar,
        Modifier.fillMaxWidth().focusRequester(foco),
        label = { Text(stringResource(R.string.cadastro_cpf)) },
        placeholder = { Text(EXEMPLO_DO_CPF) },
        singleLine = true,
        isError = erro != null,
        supportingText = textoDeApoio(erro, dica),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
    )
}

/** Data de nascimento: o campo mostra a escolhida e abre o calendário, sem datas no futuro. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CampoDeNascimento(
    data: String,
    aoEscolher: (String) -> Unit,
    erro: MensagemDeErro?,
    foco: FocusRequester,
    dica: String? = null,
) {
    var escolhendo by rememberSaveable { mutableStateOf(false) }
    val toques = remember { MutableInteractionSource() }
    LaunchedEffect(toques) {
        toques.interactions.collect { if (it is PressInteraction.Release) escolhendo = true }
    }
    OutlinedTextField(
        dataCurta(data),
        {},
        Modifier.fillMaxWidth().focusRequester(foco),
        readOnly = true,
        label = { Text(stringResource(R.string.cadastro_nascimento)) },
        isError = erro != null,
        supportingText = textoDeApoio(erro, dica),
        interactionSource = toques,
        trailingIcon = {
            IconButton({ escolhendo = true }) {
                Icon(painterResource(Icones.drawable.ic_calendario), stringResource(R.string.cadastro_escolher_data))
            }
        },
    )
    if (!escolhendo) return
    // O DatePicker trabalha com meia-noite UTC: a conversão não desloca o dia.
    val hoje = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val inicial = data.takeIf { it.isNotEmpty() }
        ?.let { runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() }
        ?: LocalDate.of(2000, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = inicial,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= hoje
        },
    )
    DatePickerDialog(
        onDismissRequest = { escolhendo = false },
        confirmButton = {
            TextButton({
                estado.selectedDateMillis?.let { aoEscolher(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                escolhendo = false
            }) { Text(stringResource(R.string.cadastro_ok)) }
        },
        dismissButton = { TextButton({ escolhendo = false }) { Text(stringResource(Comum.string.cancelar)) } },
    ) {
        DatePicker(estado)
    }
}
