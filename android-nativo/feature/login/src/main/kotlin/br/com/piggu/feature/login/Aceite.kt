package br.com.piggu.feature.login

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.VERSAO_DOS_TERMOS
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

data class EstadoDoAceite(val salvando: Boolean = false, val erro: MensagemDeErro? = null)

/** Os Termos mudaram (features/aceite): aceitar, sair ou excluir a conta. Aceitou, o app abre. */
@HiltViewModel
class AceiteViewModel @Inject constructor(
    private val sessao: Sessao,
) : ViewModel() {
    private val estadoAtual = MutableStateFlow(EstadoDoAceite())
    val estado: StateFlow<EstadoDoAceite> = estadoAtual.asStateFlow()

    fun aceitar() {
        if (estadoAtual.value.salvando) return
        estadoAtual.update { EstadoDoAceite(salvando = true) }
        viewModelScope.launch {
            try {
                sessao.aceitarTermos()
                estadoAtual.update { EstadoDoAceite() }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { EstadoDoAceite(erro = mensagemDeErro(falha)) }
            }
        }
    }

    fun sair() {
        viewModelScope.launch {
            sessao.sair()
        }
    }
}

@Composable
fun TelaDoAceite(
    abrirTermos: () -> Unit,
    abrirPrivacidade: () -> Unit,
    viewModel: AceiteViewModel = hiltViewModel(),
    exclusao: ExclusaoViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    TelaDoAceite(estado, viewModel::aceitar, viewModel::sair, exclusao::abrir, abrirTermos, abrirPrivacidade)
    DialogoDeExclusao(exclusao)
}

@Composable
fun TelaDoAceite(
    estado: EstadoDoAceite,
    aceitar: () -> Unit,
    sair: () -> Unit,
    excluir: () -> Unit,
    abrirTermos: () -> Unit,
    abrirPrivacidade: () -> Unit,
) {
    CaixaDaEntrada(stringResource(R.string.aceite_titulo), subtitulo = stringResource(R.string.aceite_subtitulo)) {
        estado.erro?.let { TextoDeErro(it.texto(LocalContext.current)) }
        Text(
            stringResource(R.string.aceite_o_que_mudou),
            Modifier.fillMaxWidth().semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        listOf(R.string.aceite_mudanca_1, R.string.aceite_mudanca_2, R.string.aceite_mudanca_3).forEach {
            Text("•  " + stringResource(it), Modifier.fillMaxWidth())
        }
        Text(stringResource(R.string.aceite_leia, VERSAO_DOS_TERMOS), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            TextButton(abrirTermos) { Text(stringResource(R.string.termos_de_uso)) }
            TextButton(abrirPrivacidade) { Text(stringResource(R.string.aviso_de_privacidade)) }
        }
        Button(aceitar, Modifier.fillMaxWidth(), enabled = !estado.salvando) { Text(stringResource(R.string.aceite_aceitar)) }
        OutlinedButton(sair, Modifier.fillMaxWidth(), enabled = !estado.salvando) { Text(stringResource(R.string.sair)) }
        TextButton(excluir, enabled = !estado.salvando) {
            Text(stringResource(R.string.aceite_excluir), color = MaterialTheme.colorScheme.error)
        }
    }
}
