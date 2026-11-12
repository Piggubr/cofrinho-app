package br.com.piggu.core.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.ui.formatos.dataCurta
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** O + flutuante fica por cima das listas das abas; o fim delas ganha esta folga (BUG-013). */
val FOLGA_DO_LANCAR = 88.dp

/**
 * Data (aaaa-mm-dd) num campo só de leitura: tocar em qualquer parte dele abre o calendário. O
 * DatePicker trabalha em milissegundos UTC: por isso a conversão por UTC, para não deslocar o dia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoDeData(rotulo: String, data: String, aoMudar: (String) -> Unit, modifier: Modifier = Modifier) {
    var aberto by rememberSaveable { mutableStateOf(false) }
    val toques = remember { MutableInteractionSource() }
    LaunchedEffect(toques) {
        toques.interactions.collect { if (it is PressInteraction.Release) aberto = true }
    }
    OutlinedTextField(
        dataCurta(data),
        {},
        modifier,
        readOnly = true,
        label = { Text(rotulo) },
        singleLine = true,
        trailingIcon = {
            IconButton({ aberto = true }) { Icon(painterResource(Icone.Calendario.desenho), stringResource(R.string.data_escolher)) }
        },
        interactionSource = toques,
    )
    if (!aberto) return
    val dia = runCatching { LocalDate.parse(data.take(10)) }.getOrNull()
    val calendario = rememberDatePickerState(dia?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
    DatePickerDialog(
        onDismissRequest = { aberto = false },
        confirmButton = {
            TextButton({
                calendario.selectedDateMillis?.let { aoMudar(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                aberto = false
            }) { Text(stringResource(R.string.data_ok)) }
        },
        dismissButton = { TextButton({ aberto = false }) { Text(stringResource(R.string.cancelar)) } },
    ) { DatePicker(calendario) }
}

/**
 * "Essa data é 01/01/1900. Lançar mesmo assim?" ([br.com.piggu.core.ui.formatos.dataPedeConfirmacao]):
 * aparece com [data] (aaaa-mm-dd) não nula.
 */
@Composable
fun ConfirmarData(data: String?, lancar: () -> Unit, corrigir: () -> Unit) {
    data ?: return
    AlertDialog(
        onDismissRequest = corrigir,
        confirmButton = { TextButton(lancar) { Text(stringResource(R.string.data_confirmar_lancar)) } },
        dismissButton = { TextButton(corrigir) { Text(stringResource(R.string.data_corrigir)) } },
        text = { Text(stringResource(R.string.data_confirmar, dataCurta(data))) },
    )
}

/**
 * Um valor em dinheiro numa linha só: com a fonte grande, a letra diminui até caber, em vez de
 * quebrar "R$ 2.462,50" no meio (BUG-014).
 */
@Composable
fun ValorQueCabe(
    texto: String,
    modifier: Modifier = Modifier,
    estilo: TextStyle = LocalTextStyle.current,
    cor: Color = Color.Unspecified,
    peso: FontWeight? = null,
) {
    Text(
        texto,
        modifier,
        color = cor,
        fontWeight = peso,
        style = estilo,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = if (estilo.fontSize.isSpecified) estilo.fontSize else 16.sp),
    )
}
