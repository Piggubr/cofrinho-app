package br.com.piggu.feature.receitas

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.ui.FalhaAoCarregar
import br.com.piggu.core.ui.formatos.dataCurta
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Aviso de sucesso para a tela: um texto do app com os argumentos. */
data class Aviso(@param:StringRes val texto: Int, val argumentos: List<Any> = emptyList())

@Composable
internal fun Aviso.texto(): String = stringResource(texto, *argumentos.toTypedArray())

@Composable
internal fun MensagemDeErro.texto(): String = texto(LocalContext.current)

/** Por que a lista não carregou, para o ícone e o texto do [br.com.piggu.core.ui.EstadoDeErro]. */
internal fun falhaDe(erro: ErroDaApi): FalhaAoCarregar = when {
    erro is ErroDaApi.SemInternet -> FalhaAoCarregar.SemInternet
    erro is ErroDaApi.DoServidor && erro.status >= 500 -> FalhaAoCarregar.DoServidor
    else -> FalhaAoCarregar.Outra
}

/** "28 de out., 10:00": quando o guardado foi gravado, como o AvisoDeGuardado do iOS. */
internal fun quandoGuardado(milissegundos: Long, zona: ZoneId): String =
    DateTimeFormatter.ofPattern("d 'de' MMM, HH:mm", Locale.forLanguageTag("pt-BR"))
        .format(Instant.ofEpochMilli(milissegundos).atZone(zona))

/** Caixa de marcar com o texto inteiro tocável (48dp no mínimo), lida pelo TalkBack como uma só. */
@Composable
internal fun CaixaDeMarcar(texto: String, marcada: Boolean, aoMudar: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(marcada, role = Role.Checkbox, onValueChange = aoMudar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(marcada, onCheckedChange = null)
        Text(texto, Modifier.padding(start = 8.dp))
    }
}

/**
 * Data (aaaa-mm-dd) num campo só de leitura que abre o calendário. O DatePicker trabalha em
 * milissegundos UTC: por isso a conversão por UTC, para não deslocar o dia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CampoDeData(rotulo: String, data: String, aoMudar: (String) -> Unit, modifier: Modifier = Modifier) {
    var aberto by rememberSaveable { mutableStateOf(false) }
    val escolher = stringResource(R.string.receitas_escolher_data)
    OutlinedTextField(
        dataCurta(data),
        {},
        modifier,
        readOnly = true,
        label = { Text(rotulo) },
        singleLine = true,
        trailingIcon = {
            IconButton({ aberto = true }) { Icon(painterResource(Icone.Calendario.desenho), escolher) }
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
            }) { Text(stringResource(R.string.receitas_ok)) }
        },
        dismissButton = { TextButton({ aberto = false }) { Text(stringResource(br.com.piggu.core.ui.R.string.cancelar)) } },
    ) { DatePicker(calendario) }
}
