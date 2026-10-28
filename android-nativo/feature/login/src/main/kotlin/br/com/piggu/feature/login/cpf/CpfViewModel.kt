package br.com.piggu.feature.login.cpf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.formatos.BRASIL
import br.com.piggu.core.ui.formatos.cpfValido
import br.com.piggu.core.ui.formatos.digitosDoCpf
import br.com.piggu.core.ui.formatos.formatarCpf
import br.com.piggu.feature.login.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EstadoDoCpf(
    val pais: String = BRASIL,
    /** Já com a máscara. */
    val documento: String = "",
    val enviando: Boolean = false,
    /** Erro do CPF digitado: vai embaixo do campo. */
    val erroNoCampo: MensagemDeErro? = null,
    /** Erro do servidor ou sem internet: vai no topo. */
    val erro: MensagemDeErro? = null,
    val tentativas: Int = 0,
    /** Gravou: a tela volta. */
    val gravado: Boolean = false,
)

/**
 * Informar o país e o CPF (features/cpf), pedido aqui e fora. [Sessao.informarPais] já renova o
 * token, para os outros serviços verem a conta completa.
 */
@HiltViewModel
class CpfViewModel @Inject constructor(private val sessao: Sessao) : ViewModel() {
    private val estadoAtual = MutableStateFlow(EstadoDoCpf(pais = sessao.usuario?.pais?.ifEmpty { null } ?: BRASIL))
    val estado: StateFlow<EstadoDoCpf> = estadoAtual.asStateFlow()

    fun mudarPais(pais: String) = estadoAtual.update { it.copy(pais = pais, erro = null) }
    fun mudarDocumento(documento: String) =
        estadoAtual.update { it.copy(documento = formatarCpf(documento), erroNoCampo = null, erro = null) }

    fun continuar() {
        val atual = estadoAtual.value
        if (atual.enviando) return
        if (!cpfValido(atual.documento)) {
            val texto = if (atual.documento.isBlank()) R.string.cadastro_falta_documento else R.string.cadastro_cpf_invalido
            estadoAtual.update { it.copy(erroNoCampo = MensagemDeErro.DoApp(texto), erro = null, tentativas = it.tentativas + 1) }
            return
        }
        estadoAtual.update { it.copy(enviando = true, erroNoCampo = null, erro = null) }
        viewModelScope.launch {
            try {
                sessao.informarPais(atual.pais, digitosDoCpf(atual.documento))
                estadoAtual.update { it.copy(enviando = false, gravado = true) }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(enviando = false, erro = mensagemDeErro(falha)) }
            }
        }
    }
}
