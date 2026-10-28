package br.com.piggu.core.data

import br.com.piggu.core.datastore.CofreDaSessao
import br.com.piggu.core.model.PedidoDeSenha
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.ServicoDeIdentidade

/**
 * Cria a senha (conta só do Google) ou troca a atual (PUT /auth/me/senha), sem derrubar a sessão
 * deste aparelho. Trocar encerra as sessões da conta, menos a do `refreshToken` mandado junto.
 *
 * <p>O refresh da memória é privado da [Sessao]. Com "Continuar conectado" o [cofre] guarda o
 * mesmo; sem, o servidor encerra esta sessão também, e o app entra de novo com a senha nova.</p>
 *
 * @param senhaAtual só para trocar; nula cria a senha
 */
suspend fun Sessao.definirSenha(identidade: ServicoDeIdentidade, cofre: CofreDaSessao, novaSenha: String, senhaAtual: String?) {
    // shortcut: uma renovação entre ler o cofre e o servidor gravar troca o refresh, e esta sessão cai na próxima renovação; mover para a Sessao se acontecer.
    val refresh = cofre.refresh()
    val usuario = identidade.definirSenha(PedidoDeSenha(senhaAtual, novaSenha, refresh))
    if (senhaAtual == null || refresh != null) {
        entrou(usuario)
        return
    }
    // A senha já mudou: sem conseguir entrar de novo agora, a sessão segue até a próxima renovação.
    try {
        entrarComSenha(usuario.email, novaSenha, lembrar = false)
    } catch (_: ErroDaApi) {
        entrou(usuario)
    }
}
