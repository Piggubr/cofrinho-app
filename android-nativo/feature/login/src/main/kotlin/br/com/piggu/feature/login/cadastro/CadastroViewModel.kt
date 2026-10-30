package br.com.piggu.feature.login.cadastro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.VERSAO_DOS_TERMOS
import br.com.piggu.core.model.PedidoDeCadastro
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.formatos.BRASIL
import br.com.piggu.core.ui.formatos.ProblemaDaSenha
import br.com.piggu.core.ui.formatos.celularCompleto
import br.com.piggu.core.ui.formatos.celularValido
import br.com.piggu.core.ui.formatos.cpfValido
import br.com.piggu.core.ui.formatos.digitosDoCpf
import br.com.piggu.core.ui.formatos.formatarCpf
import br.com.piggu.core.ui.formatos.pareceEmail
import br.com.piggu.core.ui.formatos.problemaDaSenha
import br.com.piggu.core.ui.formatos.problemaDoNascimento
import br.com.piggu.feature.login.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Os campos do cadastro, na ordem da tela (e da conferência). */
enum class CampoDoCadastro { Nome, Nascimento, Email, Senha, Confirmacao, Telefone, Documento, Aceite }

data class EstadoDoCadastro(
    val nome: String = "",
    /** aaaa-mm-dd, vazio até escolher. */
    val nascimento: String = "",
    val email: String = "",
    val senha: String = "",
    val confirmacao: String = "",
    /** País do DDI do celular; segue o país onde mora até a pessoa escolher outro. */
    val paisDoDdi: String = BRASIL,
    val telefone: String = "",
    val pais: String = BRASIL,
    /** O CPF já com a máscara. */
    val documento: String = "",
    val aceitou: Boolean = false,
    val enviando: Boolean = false,
    /** O campo da [erro]; nulo, ela vai no topo (sem internet, erro sem campo). */
    val campoComProblema: CampoDoCadastro? = null,
    val erro: MensagemDeErro? = null,
    /** Sobe a cada envio recusado: a tela leva o foco ao [campoComProblema] de novo. */
    val tentativas: Int = 0,
    /** A conta foi criada: o e-mail para onde foi o link. A tela chama aoCriar e [CadastroViewModel.avisado]. */
    val criadaPara: String? = null,
)

/**
 * Criar conta por e-mail e senha (cadastrar de login.ts). Não abre sessão: a pessoa confirma o
 * e-mail e entra com a senha. Confere aqui o que dá sem o servidor; o backend confere de novo.
 */
@HiltViewModel
class CadastroViewModel @Inject constructor(private val sessao: Sessao) : ViewModel() {
    private val estadoAtual = MutableStateFlow(EstadoDoCadastro())
    val estado: StateFlow<EstadoDoCadastro> = estadoAtual.asStateFlow()

    fun mudarNome(nome: String) = estadoAtual.update { it.copy(nome = nome.take(120)) }
    fun mudarNascimento(nascimento: String) = estadoAtual.update { it.copy(nascimento = nascimento) }
    fun mudarEmail(email: String) = estadoAtual.update { it.copy(email = email.take(320)) }
    fun mudarSenha(senha: String) = estadoAtual.update { it.copy(senha = senha) }
    fun mudarConfirmacao(confirmacao: String) = estadoAtual.update { it.copy(confirmacao = confirmacao) }
    fun mudarPaisDoDdi(pais: String) = estadoAtual.update { it.copy(paisDoDdi = pais) }
    fun mudarTelefone(telefone: String) = estadoAtual.update { it.copy(telefone = telefone.take(20)) }
    fun mudarPais(pais: String) = estadoAtual.update { it.copy(pais = pais, paisDoDdi = pais) }
    fun mudarDocumento(documento: String) = estadoAtual.update { it.copy(documento = formatarCpf(documento)) }
    fun mudarAceite(aceitou: Boolean) = estadoAtual.update { it.copy(aceitou = aceitou) }

    fun criar() {
        val atual = estadoAtual.value
        if (atual.enviando) return
        problema(atual)?.let { (campo, mensagem) ->
            recusar(campo, mensagem)
            return
        }
        estadoAtual.update { it.copy(enviando = true, campoComProblema = null, erro = null) }
        val email = atual.email.trim()
        viewModelScope.launch {
            try {
                sessao.cadastrar(
                    PedidoDeCadastro(
                        nome = atual.nome.trim(),
                        email = email,
                        senha = atual.senha,
                        pais = atual.pais,
                        documento = digitosDoCpf(atual.documento),
                        telefone = celularCompleto(atual.paisDoDdi, atual.telefone),
                        nascimento = atual.nascimento,
                        versaoDosTermos = VERSAO_DOS_TERMOS,
                    ),
                )
                // O backend responde igual se o e-mail já tem conta: a tela não entra sozinha.
                estadoAtual.update { it.copy(enviando = false, criadaPara = email) }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(enviando = false) }
                recusar(campoDoServidor(falha), mensagemDeErro(falha, R.string.cadastro_nao_criou))
            }
        }
    }

    /** A tela já levou a pessoa para "Entrar": a senha e o aceite não ficam, como no site. */
    fun avisado() = estadoAtual.update { it.copy(criadaPara = null, senha = "", confirmacao = "", aceitou = false) }

    private fun recusar(campo: CampoDoCadastro?, mensagem: MensagemDeErro) = estadoAtual.update {
        it.copy(campoComProblema = campo, erro = mensagem, tentativas = it.tentativas + 1)
    }

    /** O primeiro campo que falta ou está errado e o aviso dele (campoEProblema de login.ts). */
    private fun problema(e: EstadoDoCadastro): Pair<CampoDoCadastro, MensagemDeErro>? {
        fun app(texto: Int) = MensagemDeErro.DoApp(texto)
        return when {
            e.nome.isBlank() -> CampoDoCadastro.Nome to app(R.string.cadastro_falta_nome)
            problemaDoNascimento(e.nascimento) != null ->
                CampoDoCadastro.Nascimento to app(problemaDoNascimento(e.nascimento)!!.mensagem)
            !pareceEmail(e.email.trim()) -> CampoDoCadastro.Email to app(R.string.cadastro_confira_email)
            problemaDaSenha(e.senha, e.email) != null ->
                CampoDoCadastro.Senha to app(textoDaSenha(problemaDaSenha(e.senha, e.email)!!))
            e.senha != e.confirmacao -> CampoDoCadastro.Confirmacao to app(R.string.cadastro_senhas_diferentes)
            !celularValido(e.telefone) -> CampoDoCadastro.Telefone to app(R.string.cadastro_falta_celular)
            !cpfValido(e.documento) -> CampoDoCadastro.Documento to
                app(if (e.documento.isBlank()) R.string.cadastro_falta_documento else R.string.cadastro_cpf_invalido)
            !e.aceitou -> CampoDoCadastro.Aceite to app(R.string.cadastro_falta_aceite)
            else -> null
        }
    }
}

internal fun textoDaSenha(problema: ProblemaDaSenha): Int = when (problema) {
    ProblemaDaSenha.Curta -> R.string.cadastro_senha_curta
    ProblemaDaSenha.Longa -> R.string.cadastro_senha_longa
    ProblemaDaSenha.SemLetraOuNumero -> R.string.cadastro_senha_letras_e_numeros
    ProblemaDaSenha.IgualAoEmail -> R.string.cadastro_senha_igual_ao_email
}

/**
 * O campo de um erro do servidor: o primeiro de `campos` (VALIDACAO) que a tela tem, ou o do
 * código (API_SPEC, Erros). Nulo, a mensagem vai no topo. O site põe tudo no topo.
 */
internal fun campoDoServidor(falha: ErroDaApi): CampoDoCadastro? {
    if (falha !is ErroDaApi.DoServidor) return null
    val porNome = mapOf(
        "nome" to CampoDoCadastro.Nome,
        "nascimento" to CampoDoCadastro.Nascimento,
        "email" to CampoDoCadastro.Email,
        "senha" to CampoDoCadastro.Senha,
        "telefone" to CampoDoCadastro.Telefone,
        "documento" to CampoDoCadastro.Documento,
    )
    return falha.campos.firstNotNullOfOrNull { porNome[it.campo] } ?: when (falha.codigo) {
        "IDADE_MINIMA" -> CampoDoCadastro.Nascimento
        "SENHA_FRACA" -> CampoDoCadastro.Senha
        "TELEFONE_INVALIDO" -> CampoDoCadastro.Telefone
        "CPF_NECESSARIO", "CPF_EM_USO" -> CampoDoCadastro.Documento
        else -> null
    }
}
