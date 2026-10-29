package br.com.piggu.feature.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.mensagemDeErro
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EstadoDaExclusao(
    val aberta: Boolean = false,
    val digitado: String = "",
    val excluindo: Boolean = false,
    val erro: MensagemDeErro? = null,
)

/** Só a palavra EXCLUIR, em qualquer caixa e sem espaço nas pontas, confirma (o prompt do site). */
fun confirmaAExclusao(digitado: String): Boolean = digitado.trim().uppercase() == "EXCLUIR"

/**
 * Excluir a conta (DELETE /api/me), do Aceite e do Mais, como o site: o texto do que se perde e
 * a palavra EXCLUIR. Deu certo, a sessão acaba e o app volta ao login.
 */
@HiltViewModel
class ExclusaoViewModel @Inject constructor(private val sessao: Sessao) : ViewModel() {
    private val estadoAtual = MutableStateFlow(EstadoDaExclusao())
    val estado: StateFlow<EstadoDaExclusao> = estadoAtual.asStateFlow()

    fun abrir() = estadoAtual.update { EstadoDaExclusao(aberta = true) }
    fun fechar() = estadoAtual.update { it.copy(aberta = false) }
    fun digitar(texto: String) = estadoAtual.update { it.copy(digitado = texto, erro = null) }

    fun excluir() {
        if (!confirmaAExclusao(estadoAtual.value.digitado) || estadoAtual.value.excluindo) return
        estadoAtual.update { it.copy(excluindo = true, erro = null) }
        viewModelScope.launch {
            try {
                sessao.excluirConta()
                estadoAtual.update { EstadoDaExclusao() }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(excluindo = false, erro = mensagemDeErro(falha)) }
            }
        }
    }
}

/** O diálogo de confirmar; abre com [ExclusaoViewModel.abrir]. */
@Composable
fun DialogoDeExclusao(viewModel: ExclusaoViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    DialogoDeExclusao(estado, viewModel::digitar, viewModel::excluir, viewModel::fechar)
}

@Composable
fun DialogoDeExclusao(estado: EstadoDaExclusao, digitar: (String) -> Unit, excluir: () -> Unit, fechar: () -> Unit) {
    if (!estado.aberta) return
    AlertDialog(
        onDismissRequest = { if (!estado.excluindo) fechar() },
        title = { Text(stringResource(R.string.excluir_titulo)) },
        text = {
            Column {
                Text(stringResource(R.string.excluir_texto))
                OutlinedTextField(
                    estado.digitado,
                    digitar,
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = { Text(stringResource(R.string.excluir_campo)) },
                    singleLine = true,
                    enabled = !estado.excluindo,
                )
                estado.erro?.let { TextoDeErro(it.texto(LocalContext.current), Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(excluir, enabled = confirmaAExclusao(estado.digitado) && !estado.excluindo) {
                Text(
                    stringResource(if (estado.excluindo) R.string.excluindo else R.string.excluir_confirmar),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = { TextButton(fechar, enabled = !estado.excluindo) { Text(stringResource(R.string.cancelar)) } },
    )
}
