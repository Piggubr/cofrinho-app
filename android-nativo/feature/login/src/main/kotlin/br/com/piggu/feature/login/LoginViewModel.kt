package br.com.piggu.feature.login

import androidx.lifecycle.ViewModel
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

enum class ModoDaEntrada { Entrar, CriarConta }

/** O que a tela de entrada mostra. "Continuar conectado" começa ligado, como no site. */
data class EstadoDoLogin(
    val modo: ModoDaEntrada = ModoDaEntrada.Entrar,
    val email: String = "",
    val senha: String = "",
    val lembrar: Boolean = true,
    val entrando: Boolean = false,
    val erro: MensagemDeErro? = null,
    /** Conta criada: "confira seu e-mail" sobre o formulário de entrar. */
    val contaCriada: Boolean = false,
    /** Conta nova pelo Google: ele já confirmou, falta o aceite dos termos para criar. */
    val aguardandoAceite: Boolean = false,
    val aceitou: Boolean = false,
    /** Entrou com senha: a tela oferece guardar no gerenciador de senhas e chama [LoginViewModel.senhaOferecida]. */
    val senhaParaSalvar: Pair<String, String>? = null,
)

/** Entrada no app (login.ts): e-mail e senha, ou o Google, com o aceite na conta nova. */
@HiltViewModel
class LoginViewModel @Inject constructor(private val sessao: Sessao) : ViewModel() {
    private val estadoAtual = MutableStateFlow(EstadoDoLogin())
    val estado: StateFlow<EstadoDoLogin> = estadoAtual.asStateFlow()

    /** O idToken do Google guardado enquanto a pessoa aceita os termos. */
    private var tokenDoGoogle = ""

    fun mudarEmail(email: String) = estadoAtual.update { it.copy(email = email) }
    fun mudarSenha(senha: String) = estadoAtual.update { it.copy(senha = senha) }
    fun mudarLembrar(lembrar: Boolean) = estadoAtual.update { it.copy(lembrar = lembrar) }

    /** Troca entre entrar e criar conta; o e-mail digitado continua lá. */
    fun trocarModo(modo: ModoDaEntrada) = estadoAtual.update {
        it.copy(modo = modo, erro = null, contaCriada = false, senha = "", aceitou = false)
    }

    /** Criou a conta: volta para entrar, com o e-mail e o aviso de confirmar. */
    fun contaCriada(email: String) = estadoAtual.update {
        it.copy(modo = ModoDaEntrada.Entrar, email = email, senha = "", erro = null, contaCriada = true)
    }

    fun entrar() {
        val atual = estadoAtual.value
        if (atual.entrando) return
        if (atual.email.isBlank() || atual.senha.isEmpty()) {
            estadoAtual.update { it.copy(erro = MensagemDeErro.DoApp(R.string.login_falta_email_e_senha)) }
            return
        }
        tentar {
            sessao.entrarComSenha(atual.email, atual.senha, atual.lembrar)
            estadoAtual.update { it.copy(senha = "", senhaParaSalvar = atual.email.trim() to atual.senha) }
        }
    }

    fun senhaOferecida() = estadoAtual.update { it.copy(senhaParaSalvar = null) }

    fun aoVoltarDoGoogle(resultado: ResultadoDoGoogle) {
        when (resultado) {
            is ResultadoDoGoogle.Token -> entrarPeloGoogle(resultado.idToken, null)
            ResultadoDoGoogle.Cancelado -> estadoAtual.update { it.copy(erro = null) }
            ResultadoDoGoogle.Falhou -> estadoAtual.update { it.copy(erro = MensagemDeErro.DoApp(R.string.login_google_falhou)) }
        }
    }

    fun marcarAceite(aceitou: Boolean) = estadoAtual.update { it.copy(aceitou = aceitou) }

    /** Cria a conta Google com o aceite, reaproveitando a confirmação que já chegou. */
    fun criarContaComGoogle() {
        if (estadoAtual.value.aceitou) entrarPeloGoogle(tokenDoGoogle, VERSAO_DOS_TERMOS)
    }

    fun voltarDoAceite() {
        tokenDoGoogle = ""
        estadoAtual.update { it.copy(aguardandoAceite = false, aceitou = false) }
    }

    private fun entrarPeloGoogle(idToken: String, versaoDosTermos: String?) {
        tentar(tratar = { falha ->
            if (falha !is ErroDaApi.DoServidor || falha.codigo != "TERMOS_NECESSARIOS") return@tentar false
            tokenDoGoogle = idToken
            estadoAtual.update { it.copy(aguardandoAceite = true, aceitou = false) }
            true
        }) {
            sessao.entrarComGoogle(idToken, estadoAtual.value.lembrar, versaoDosTermos)
            tokenDoGoogle = ""
            estadoAtual.update { it.copy(aguardandoAceite = false) }
        }
    }

    /** [tratar] devolve verdadeiro quando já cuidou da falha (sem mensagem de erro). */
    private fun tentar(tratar: (ErroDaApi) -> Boolean = { false }, acao: suspend () -> Unit) {
        estadoAtual.update { it.copy(entrando = true, erro = null, contaCriada = false) }
        viewModelScope.launch {
            try {
                acao()
                estadoAtual.update { it.copy(entrando = false) }
            } catch (falha: ErroDaApi) {
                val tratada = tratar(falha)
                estadoAtual.update {
                    it.copy(entrando = false, erro = if (tratada) null else mensagemDeErro(falha, R.string.login_nao_entrou))
                }
            }
        }
    }
}
