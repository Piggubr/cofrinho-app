package br.com.piggu.core.testing

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.CopyOnWriteArrayList

/** Uma resposta para "METODO /api/rota" (o caminho sem a consulta). */
data class RespostaFixa(val status: Int, val corpo: String = "")

/**
 * Responde algumas rotas com o que o teste mandar e deixa o resto seguir (para a [ApiDaDemo],
 * que vem depois). Serve aos casos que a demonstração nunca dá: senha errada, e-mail não
 * confirmado, termos pendentes, cadastro incompleto. Guarda o que foi pedido em [pedidos].
 */
class RespostasFixas(private val respostas: Map<String, RespostaFixa>) : Interceptor {
    constructor(vararg respostas: Pair<String, RespostaFixa>) : this(respostas.toMap())

    /** "POST /api/auth/entrar {corpo}" de cada chamada, na ordem. */
    val pedidos: MutableList<String> = CopyOnWriteArrayList()

    override fun intercept(chain: Interceptor.Chain): Response {
        val pedido = chain.request()
        val chave = "${pedido.method} ${pedido.url.encodedPath}"
        val corpo = pedido.body?.let { okio.Buffer().also(it::writeTo).readUtf8() }.orEmpty()
        pedidos += "$chave $corpo".trim()
        val fixa = respostas[chave] ?: return chain.proceed(pedido)
        return Response.Builder()
            .request(pedido)
            .protocol(Protocol.HTTP_1_1)
            .code(fixa.status)
            .message("Piggu de teste")
            .body(fixa.corpo.toResponseBody("application/json".toMediaType()))
            .build()
    }

    companion object {
        /** O corpo de erro do backend (ApiError do API_SPEC). */
        fun erro(status: Int, codigo: String, mensagem: String) =
            RespostaFixa(status, """{"erro":"$mensagem","codigo":"$codigo","campos":[],"momento":"2026-10-28T13:00:00Z"}""")
    }
}
