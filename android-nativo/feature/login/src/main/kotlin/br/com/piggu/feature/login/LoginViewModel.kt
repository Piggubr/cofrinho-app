package br.com.piggu.feature.login

import androidx.lifecycle.ViewModel
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

/** Entrar por e-mail e senha (entrarComSenha de login.ts). Deu certo, a sessão muda e o app sai do login. */
@HiltViewModel
class LoginViewModel @Inject constructor(private val sessao: Sessao) : ViewModel() {
    private val estadoAtual = MutableStateFlow(EstadoDoLogin())
    val estado: StateFlow<EstadoDoLogin> = estadoAtual.asStateFlow()

    fun mudarEmail(email: String) = estadoAtual.update { it.copy(email = email) }
    fun mudarSenha(senha: String) = estadoAtual.update { it.copy(senha = senha) }
    fun mudarLembrar(lembrar: Boolean) = estadoAtual.update { it.copy(lembrar = lembrar) }

    fun entrar() {
        val atual = estadoAtual.value
        if (atual.entrando) return
        if (atual.email.isBlank() || atual.senha.isEmpty()) {
            estadoAtual.update { it.copy(erro = MensagemDeErro.DoApp(R.string.login_falta_email_e_senha)) }
            return
        }
        estadoAtual.update { it.copy(entrando = true, erro = null) }
        viewModelScope.launch {
            try {
                sessao.entrarComSenha(atual.email, atual.senha, atual.lembrar)
                estadoAtual.update { it.copy(entrando = false, senha = "") }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(entrando = false, erro = mensagemDeErro(falha, R.string.login_nao_entrou)) }
            }
        }
    }
}
