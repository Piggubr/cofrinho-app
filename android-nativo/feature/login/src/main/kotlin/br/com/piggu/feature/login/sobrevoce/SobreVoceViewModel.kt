package br.com.piggu.feature.login.sobrevoce

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.model.PedidoSobreVoce
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.formatos.BRASIL
import br.com.piggu.core.ui.formatos.celularCompleto
import br.com.piggu.core.ui.formatos.celularValido
import br.com.piggu.core.ui.formatos.cpfValido
import br.com.piggu.core.ui.formatos.digitosDoCpf
import br.com.piggu.core.ui.formatos.formatarCpf
import br.com.piggu.core.ui.formatos.problemaDoNascimento
import br.com.piggu.feature.login.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CampoSobreVoce { Apelido, Nascimento, Cpf, Telefone }

data class EstadoSobreVoce(
    val apelido: String = "",
    /** Só aparecem os campos que faltam na conta; nascimento e CPF não mudam depois. */
    val pedeNascimento: Boolean = false,
    val pedeCpf: Boolean = false,
    val pedeCelular: Boolean = false,
    val nascimento: String = "",
    val cpf: String = "",
    val paisDoDdi: String = BRASIL,
    val telefone: String = "",
    val enviando: Boolean = false,
    val campoComProblema: CampoSobreVoce? = null,
    val erro: MensagemDeErro? = null,
    val tentativas: Int = 0,
)

/**
 * Completa o cadastro (features/sobre-voce, D-088): o apelido sempre; nascimento, CPF e celular
 * só se faltam. Deu certo, a sessão muda e o app sai da pendência sozinho.
 */
@HiltViewModel
class SobreVoceViewModel @Inject constructor(private val sessao: Sessao) : ViewModel() {
    private val estadoAtual = MutableStateFlow(
        sessao.usuario.let { usuario ->
            EstadoSobreVoce(
                apelido = usuario?.apelido ?: usuario?.primeiroNome.orEmpty(),
                pedeNascimento = usuario?.nascimento.isNullOrEmpty(),
                pedeCpf = usuario?.cpfInformado == false,
                pedeCelular = usuario?.celularInformado == false,
                paisDoDdi = usuario?.pais ?: BRASIL,
            )
        },
    )
    val estado: StateFlow<EstadoSobreVoce> = estadoAtual.asStateFlow()

    fun mudarApelido(apelido: String) = estadoAtual.update { it.copy(apelido = apelido.take(120)) }
    fun mudarNascimento(nascimento: String) = estadoAtual.update { it.copy(nascimento = nascimento) }
    fun mudarCpf(cpf: String) = estadoAtual.update { it.copy(cpf = formatarCpf(cpf)) }
    fun mudarPaisDoDdi(pais: String) = estadoAtual.update { it.copy(paisDoDdi = pais) }
    fun mudarTelefone(telefone: String) = estadoAtual.update { it.copy(telefone = telefone.take(20)) }

    fun continuar() {
        val e = estadoAtual.value
        if (e.enviando) return
        problema(e)?.let { (campo, mensagem) ->
            recusar(campo, mensagem)
            return
        }
        estadoAtual.update { it.copy(enviando = true, campoComProblema = null, erro = null) }
        viewModelScope.launch {
            try {
                sessao.sobreVoce(
                    PedidoSobreVoce(
                        apelido = e.apelido.trim(),
                        nascimento = e.nascimento.takeIf { e.pedeNascimento },
                        cpf = digitosDoCpf(e.cpf).takeIf { e.pedeCpf },
                        telefone = celularCompleto(e.paisDoDdi, e.telefone).takeIf { e.pedeCelular },
                    ),
                )
                estadoAtual.update { it.copy(enviando = false) }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(enviando = false) }
                recusar(null, mensagemDeErro(falha))
            }
        }
    }

    private fun recusar(campo: CampoSobreVoce?, mensagem: MensagemDeErro) = estadoAtual.update {
        it.copy(campoComProblema = campo, erro = mensagem, tentativas = it.tentativas + 1)
    }

    /** Na ordem do site: apelido, nascimento, CPF e celular. */
    private fun problema(e: EstadoSobreVoce): Pair<CampoSobreVoce, MensagemDeErro>? {
        val nascimento = if (e.pedeNascimento) problemaDoNascimento(e.nascimento) else null
        return when {
            e.apelido.isBlank() -> CampoSobreVoce.Apelido to MensagemDeErro.DoApp(R.string.cadastro_falta_nome)
            nascimento != null -> CampoSobreVoce.Nascimento to MensagemDeErro.DoApp(nascimento.mensagem)
            e.pedeCpf && !cpfValido(e.cpf) -> CampoSobreVoce.Cpf to
                MensagemDeErro.DoApp(if (e.cpf.isBlank()) R.string.sobre_voce_falta_cpf else R.string.cadastro_cpf_invalido)
            e.pedeCelular && !celularValido(e.telefone) ->
                CampoSobreVoce.Telefone to MensagemDeErro.DoApp(R.string.cadastro_falta_celular)
            else -> null
        }
    }
}
