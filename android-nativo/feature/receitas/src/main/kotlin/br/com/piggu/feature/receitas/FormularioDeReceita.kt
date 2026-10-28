package br.com.piggu.feature.receitas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.core.ui.CampoDeValor
import java.math.BigDecimal
import br.com.piggu.core.ui.R as Comum

data class EventosDoFormularioDeReceita(
    val mudarValor: (BigDecimal?) -> Unit = {},
    val mudarDescricao: (String) -> Unit = {},
    val mudarCategoria: (String) -> Unit = {},
    val mudarData: (String) -> Unit = {},
    val mudarRepetir: (Boolean) -> Unit = {},
    val mudarEmDuasParcelas: (Boolean) -> Unit = {},
    val mudarSegundoDia: (String) -> Unit = {},
    val mudarSegundoValor: (BigDecimal?) -> Unit = {},
    val mudarCompartilhada: (Boolean) -> Unit = {},
    val lancar: () -> Unit = {},
)

/**
 * Nova receita, ligada ao [FormularioDeReceitaViewModel]: na aba Receitas e no + do app. Rola
 * sozinho, então vai direto na folha, sem outro scroll em volta. Depois de lançar, entrega a
 * mensagem ("Receita lançada.") em [aoLancar], uma vez; a lista recarrega por [br.com.piggu.core.data.Lancamentos].
 */
@Composable
fun FormularioDeReceita(
    aoLancar: (mensagem: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FormularioDeReceitaViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val mensagem = estado.lancada?.texto()
    LaunchedEffect(mensagem) {
        if (mensagem != null) {
            aoLancar(mensagem)
            viewModel.lancadaEntregue()
        }
    }
    FormularioDeReceita(
        estado,
        EventosDoFormularioDeReceita(
            mudarValor = viewModel::mudarValor,
            mudarDescricao = viewModel::mudarDescricao,
            mudarCategoria = viewModel::mudarCategoria,
            mudarData = viewModel::mudarData,
            mudarRepetir = viewModel::mudarRepetir,
            mudarEmDuasParcelas = viewModel::mudarEmDuasParcelas,
            mudarSegundoDia = viewModel::mudarSegundoDia,
            mudarSegundoValor = viewModel::mudarSegundoValor,
            mudarCompartilhada = viewModel::mudarCompartilhada,
            lancar = viewModel::lancar,
        ),
        modifier,
    )
}

/** O formulário só desenhado (capturas e prévias). */
@Composable
fun FormularioDeReceita(estado: EstadoDoFormularioDeReceita, eventos: EventosDoFormularioDeReceita, modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        estado.erro?.let {
            Text(it.texto(), Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, color = MaterialTheme.colorScheme.error)
        }
        CampoDeValor(estado.valor, eventos.mudarValor, estado.moeda, Modifier.fillMaxWidth())
        OutlinedTextField(
            estado.descricao,
            eventos.mudarDescricao,
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.receita_descricao)) },
            placeholder = { Text(stringResource(R.string.receita_descricao_exemplo)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        CategoriaDaReceita(estado.opcoesDeCategoria, estado.categoria, eventos.mudarCategoria)
        CampoDeData(stringResource(R.string.receita_data), estado.data, eventos.mudarData, Modifier.fillMaxWidth())
        CaixaDeMarcar(stringResource(R.string.receita_repetir, estado.diaDaReceita), estado.repetir, eventos.mudarRepetir)
        if (estado.repetir) {
            CaixaDeMarcar(stringResource(R.string.receita_duas_parcelas), estado.emDuasParcelas, eventos.mudarEmDuasParcelas)
            if (estado.emDuasParcelas) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        estado.segundoDia,
                        eventos.mudarSegundoDia,
                        Modifier.weight(1f),
                        label = { Text(stringResource(R.string.receita_segundo_dia)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    )
                    CampoDeValor(
                        estado.segundoValor,
                        eventos.mudarSegundoValor,
                        estado.moeda,
                        Modifier.weight(1f),
                        rotulo = stringResource(R.string.receita_segundo_valor),
                    )
                }
            }
        }
        if (estado.temCompanhia) {
            CaixaDeMarcar(stringResource(R.string.receita_compartilhada), estado.compartilhadaEfetiva, eventos.mudarCompartilhada)
        }
        Button(eventos.lancar, Modifier.fillMaxWidth(), enabled = !estado.salvando) {
            Text(stringResource(R.string.receita_lancar))
        }
    }
}

/** As categorias de receita são fixas (GET /incomes/categories): só escolher, sem criar como no gasto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriaDaReceita(opcoes: List<String>, escolhida: String, aoEscolher: (String) -> Unit) {
    var aberta by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(aberta, { aberta = it }) {
        OutlinedTextField(
            escolhida,
            {},
            Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            label = { Text(stringResource(Comum.string.categoria)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(aberta) },
        )
        ExposedDropdownMenu(aberta, { aberta = false }) {
            opcoes.forEach { opcao ->
                DropdownMenuItem({ Text(opcao) }, { aoEscolher(opcao); aberta = false })
            }
        }
    }
}
