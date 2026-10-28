package br.com.piggu.core.data

import br.com.piggu.core.network.AutenticacaoDaRede
import br.com.piggu.core.network.lerCorpoDeErro
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/** Rotas que não levam token: são justamente as que servem para obter um (auth.interceptor.ts). */
private val ROTAS_ABERTAS = listOf("/auth/google", "/auth/entrar", "/auth/cadastro", "/auth/refresh", "/auth/logout")

private fun Request.aberta() = ROTAS_ABERTAS.any { url.encodedPath.endsWith(it) }

/**
 * O Bearer em cada chamada e a renovação no 401, uma única vez por chamada. Chamada feita antes
 * de o token chegar (o app abriu com o perfil guardado) espera a renovação em curso, em vez de
 * levar um 401 cada uma (D-095 do iOS). O OkHttp roda isto fora da thread principal.
 */
@Singleton
class AutenticacaoDaSessao @Inject constructor(private val sessao: Sessao) : AutenticacaoDaRede {

    override val interceptador = Interceptor { corrente ->
        val pedido = corrente.request()
        if (pedido.aberta()) return@Interceptor corrente.proceed(pedido)
        val token = sessao.acesso ?: runBlocking { sessao.tokenNovo(null) }
        val resposta = corrente.proceed(if (token == null) pedido else pedido.comToken(token))
        // O token diz "cadastro incompleto" (D-088): o app leva para o "Sobre você".
        if (resposta.code == 403 && lerCorpoDeErro(resposta.peekBody(16 * 1024L).string())?.codigo == "CADASTRO_PENDENTE") {
            sessao.cadastroPendente()
        }
        resposta
    }

    override val autenticador = object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            val pedido = response.request
            // Já repetida uma vez, ou rota de entrar: o 401 segue para quem chamou.
            if (pedido.aberta() || response.priorResponse != null) return null
            val recusado = pedido.header("Authorization")?.removePrefix("Bearer ")
            val novo = runBlocking { sessao.tokenNovo(recusado) } ?: return null
            return pedido.comToken(novo)
        }
    }

    private fun Request.comToken(token: String) = newBuilder().header("Authorization", "Bearer $token").build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ModuloDaSessao {
    @Binds
    abstract fun autenticacao(sessao: AutenticacaoDaSessao): AutenticacaoDaRede
}
