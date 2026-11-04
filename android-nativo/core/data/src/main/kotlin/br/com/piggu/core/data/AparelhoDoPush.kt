package br.com.piggu.core.data

import br.com.piggu.core.datastore.CofreDaSessao
import br.com.piggu.core.model.PedidoDeAparelho
import br.com.piggu.core.network.ServicoDeIdentidade
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** O token do FCM deste aparelho; nulo sem Firebase (a demo) ou com as notificações desligadas. */
fun interface FonteDoTokenDoPush {
    suspend fun token(): String?
}

/**
 * Registra o aparelho para o push (D-166), como o Notificacoes do iOS: a cada abertura com o aviso
 * no celular ligado no Perfil, e quando o FCM troca o token. Ao sair, a [Sessao] tira o aparelho.
 */
@Singleton
class AparelhoDoPush @Inject constructor(
    private val sessao: Sessao,
    private val identidade: Lazy<ServicoDeIdentidade>,
    private val cofre: CofreDaSessao,
    private val fonte: FonteDoTokenDoPush,
    @EscopoDoApp private val escopo: CoroutineScope,
) {
    /** Registra quando a pessoa entra ou liga o aviso no celular. */
    fun vigiar() {
        escopo.launch {
            sessao.estado.map { (it as? EstadoDaSessao.Logado)?.usuario?.let { u -> u.id to (u.avisos?.push == true) } }
                .distinctUntilChanged()
                .collect { if (it?.second == true) registrar() }
        }
    }

    /** O FCM trocou o token: só vale para quem está logado e quer o aviso no celular. */
    fun tokenNovo(token: String) {
        escopo.launch { if (querPush()) registrar(token) }
    }

    /** Sem rede ou com o servidor fora, a próxima abertura tenta de novo. */
    suspend fun registrar(token: String? = null) {
        val atual = token ?: fonte.token() ?: return
        runCatching { identidade.get().registrarAparelho(PedidoDeAparelho(atual)) }
            .onSuccess { cofre.guardarTokenDoPush(atual) }
    }

    private fun querPush() = (sessao.estado.value as? EstadoDaSessao.Logado)?.usuario?.avisos?.push == true
}
