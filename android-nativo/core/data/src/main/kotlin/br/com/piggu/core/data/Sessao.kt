package br.com.piggu.core.data

import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.datastore.CofreDaSessao
import br.com.piggu.core.model.JsonDoPiggu
import br.com.piggu.core.model.ParDeTokens
import br.com.piggu.core.model.PedidoComToken
import br.com.piggu.core.model.PedidoDeCadastro
import br.com.piggu.core.model.PedidoDeEmail
import br.com.piggu.core.model.PedidoDeLogin
import br.com.piggu.core.model.PedidoDePais
import br.com.piggu.core.model.PedidoDeSenhaNova
import br.com.piggu.core.model.PedidoDeTermos
import br.com.piggu.core.model.PedidoDoGoogle
import br.com.piggu.core.model.PedidoSobreVoce
import br.com.piggu.core.model.PedidoDeRefresh
import br.com.piggu.core.model.Usuario
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.ServicoDeIdentidade
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface EstadoDaSessao {
    /** Abrindo o app, sem perfil guardado: ainda perguntando ao servidor. */
    data object Verificando : EstadoDaSessao
    data object Deslogado : EstadoDaSessao
    data class Logado(val usuario: Usuario) : EstadoDaSessao
}

/**
 * Entrada e saída da conta (auth.service.ts e Sessao.swift).
 *
 * <p>O access token fica só na memória. O refresh vem no corpo (`refreshNoCorpo`, D-072) e, com
 * "Continuar conectado", vai cifrado para o [CofreDaSessao] junto com o perfil; sem, fica só na
 * memória e some com o processo. Com o perfil guardado, o app abre logado na hora e renova por
 * trás (D-095 do iOS).</p>
 *
 * <p>O backend troca o refresh a cada uso, então só existe uma renovação por vez: quem chega
 * durante uma espera por ela e usa o token novo ([tokenNovo]).</p>
 */
@Singleton
class Sessao @Inject constructor(
    private val identidade: Lazy<ServicoDeIdentidade>,
    private val cofre: CofreDaSessao,
    private val cache: RespostasGuardadasDao,
    @EscopoDoApp private val escopo: CoroutineScope,
) {
    private val estadoAtual = MutableStateFlow<EstadoDaSessao>(EstadoDaSessao.Verificando)
    val estado: StateFlow<EstadoDaSessao> = estadoAtual.asStateFlow()

    val usuario: Usuario? get() = (estadoAtual.value as? EstadoDaSessao.Logado)?.usuario

    private val trava = Mutex()

    @Volatile var acesso: String? = null
        private set

    @Volatile private var refresh: String? = null
    private var lembrar = false

    /** Ao abrir o app. */
    suspend fun restaurar() {
        val guardado = cofre.refresh()
        if (guardado == null) {
            estadoAtual.value = EstadoDaSessao.Deslogado
            return
        }
        refresh = guardado
        lembrar = true
        val perfil = cofre.perfil()?.let { runCatching { JsonDoPiggu.decodeFromString(Usuario.serializer(), it) }.getOrNull() }
        perfil?.let { estadoAtual.value = EstadoDaSessao.Logado(it) }
        try {
            trava.withLock { renovarSemTrava() }
        } catch (erro: ErroDaApi) {
            // Sem rede ou servidor fora não é sessão vencida: segue com o guardado, e a primeira
            // chamada que der certo renova sozinha. Sem perfil guardado não há o que mostrar.
            if (recusada(erro)) encerrarLocalmente()
            else if (perfil == null) estadoAtual.value = EstadoDaSessao.Deslogado
        }
    }

    suspend fun entrarComSenha(email: String, senha: String, lembrar: Boolean) {
        abrirSessao(identidade.get().entrarComSenha(PedidoDeLogin(email.trim(), senha, lembrar)), lembrar)
    }

    /**
     * Troca o ID token do Google por uma sessão do Piggu. [versaoDosTermos] só na conta nova,
     * depois do TERMOS_NECESSARIOS: o site pede o aceite e manda de novo.
     */
    suspend fun entrarComGoogle(idToken: String, lembrar: Boolean, versaoDosTermos: String? = null) {
        abrirSessao(identidade.get().entrarComGoogle(PedidoDoGoogle(idToken, versaoDosTermos, lembrar)), lembrar)
    }

    // Sem sessão (cadastro e os links do e-mail).

    /** Cria a conta sem abrir sessão: a pessoa confirma o e-mail e entra com a senha. Responde igual se o e-mail já existe. */
    suspend fun cadastrar(pedido: PedidoDeCadastro) = identidade.get().cadastrar(pedido)

    /** Pede o link de senha nova; o backend responde igual com ou sem conta. */
    suspend fun esqueciSenha(email: String) = identidade.get().esqueciSenha(PedidoDeEmail(email.trim()))

    /** Senha nova pelo link do e-mail. Todas as sessões da conta caem: se havia uma aqui, sai dela também. */
    suspend fun redefinirSenha(token: String, novaSenha: String) {
        identidade.get().redefinirSenha(PedidoDeSenhaNova(token, novaSenha))
        if (usuario != null) encerrarLocalmente()
    }

    /** Confirma o e-mail pelo link; com sessão aberta, o perfil já sai atualizado. */
    suspend fun confirmarEmail(token: String) {
        identidade.get().confirmarEmail(PedidoComToken(token))
        if (usuario != null) atualizarPerfil()
    }

    // Completar o cadastro (auth.guard.ts) e a conta.

    /** Manda outro link de confirmação; o anterior deixa de valer. */
    suspend fun reenviarConfirmacao() = identidade.get().reenviarConfirmacao()

    /** Aceite da versão vigente dos Termos e do Aviso de Privacidade. */
    suspend fun aceitarTermos() = entrou(identidade.get().aceitarTermos(PedidoDeTermos(VERSAO_DOS_TERMOS)))

    /**
     * Como quer ser chamado e, só na primeira vez, nascimento, CPF e celular (D-088). Com CPF ou
     * celular, renova o token: o de antes diz "cadastro incompleto" aos outros serviços.
     */
    suspend fun sobreVoce(pedido: PedidoSobreVoce) {
        val usuario = identidade.get().sobreVoce(pedido)
        if (pedido.cpf != null || pedido.telefone != null) runCatching { tokenNovo(acesso) }
        entrou(usuario)
    }

    /** País onde mora e o documento. Renova o token depois: sem isso os outros serviços ainda veem a conta incompleta. */
    suspend fun informarPais(pais: String, documento: String?) {
        entrou(identidade.get().informarPais(PedidoDePais(pais, documento)))
        tokenNovo(acesso)
    }

    /** Um serviço recusou com 403 CADASTRO_PENDENTE: a raiz do app passa a mostrar o "Sobre você". */
    fun cadastroPendente() {
        val atual = usuario ?: return
        if (atual.cadastroCompleto != false) estadoAtual.value = EstadoDaSessao.Logado(atual.copy(cadastroCompleto = false))
    }

    /** Apaga a conta no servidor e sai deste aparelho. */
    suspend fun excluirConta() {
        identidade.get().excluirConta()
        encerrarLocalmente()
    }

    private suspend fun abrirSessao(tokens: ParDeTokens, lembrar: Boolean) {
        // O cache é da conta: o de quem entrou antes não serve para esta.
        cache.apagarTudo()
        trava.withLock {
            this.lembrar = lembrar
            if (!lembrar) cofre.apagar()
            guardar(tokens)
        }
    }

    /** O perfil atualizado (GET /api/auth/me); falhar não mexe na sessão. */
    suspend fun atualizarPerfil() {
        runCatching { identidade.get().meuPerfil() }.onSuccess { entrou(it) }
    }

    /** Uma tela mudou o perfil (preferências, módulos): a sessão passa a mostrar o novo. */
    suspend fun entrou(usuario: Usuario) {
        if (lembrar) cofre.guardarPerfil(JsonDoPiggu.encodeToString(Usuario.serializer(), usuario))
        estadoAtual.value = EstadoDaSessao.Logado(usuario)
    }

    /**
     * O token para repetir uma chamada recusada com [recusado] (ou feita sem token nenhum): o de
     * uma renovação que terminou enquanto ela esperava, ou o de uma renovação nova. Nulo quando não
     * há sessão ou a renovação falhou; se o servidor recusou o refresh, a sessão acaba.
     */
    suspend fun tokenNovo(recusado: String?): String? = trava.withLock {
        acesso?.takeIf { it != recusado }?.let { return@withLock it }
        if (refresh == null) return@withLock null
        try {
            renovarSemTrava().accessToken
        } catch (erro: ErroDaApi) {
            if (recusada(erro)) escopo.launch { encerrarLocalmente() }
            null
        }
    }

    private suspend fun renovarSemTrava(): ParDeTokens {
        val resposta = try {
            runInterruptible(Dispatchers.IO) { identidade.get().renovarNaHora(PedidoDeRefresh(refresh)).execute() }
        } catch (erro: Exception) {
            throw ErroDaApi.de(erro)
        }
        if (!resposta.isSuccessful) throw ErroDaApi.de(resposta)
        val tokens = resposta.body() ?: throw ErroDaApi.RespostaInvalida(IllegalStateException("refresh sem corpo"))
        guardar(tokens)
        return tokens
    }

    private suspend fun guardar(tokens: ParDeTokens) {
        acesso = tokens.accessToken
        tokens.refreshToken?.let { novo ->
            refresh = novo
            if (lembrar) cofre.guardarRefresh(novo)
        }
        entrou(tokens.usuario)
    }

    /** Sai neste aparelho: avisa o servidor (falhar não impede de sair) e apaga o que é da conta. */
    suspend fun sair() {
        runCatching { identidade.get().sair(PedidoDeRefresh(refresh)) }
        encerrarLocalmente()
    }

    /** Descarta a sessão sem falar com o servidor: tokens, perfil e cache. */
    suspend fun encerrarLocalmente() {
        acesso = null
        refresh = null
        lembrar = false
        cofre.apagar()
        cache.apagarTudo()
        estadoAtual.value = EstadoDaSessao.Deslogado
    }

    /** Só a renovação recusada com 401 ou 403 encerra a sessão; sem sinal, servidor fora ou 429, não (D-075). */
    private fun recusada(erro: ErroDaApi) = erro is ErroDaApi.DoServidor && (erro.status == 401 || erro.status == 403)
}
