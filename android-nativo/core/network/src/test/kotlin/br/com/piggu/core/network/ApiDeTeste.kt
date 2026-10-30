package br.com.piggu.core.network

import br.com.piggu.core.model.JsonDoPiggu
import java.io.Closeable
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Authenticator
import okhttp3.Headers.Companion.headersOf
import okhttp3.Interceptor

/** A sessão é de outra parte do app: nos testes da rede ela não faz nada. */
object SemAutenticacao : AutenticacaoDaRede {
    override val interceptador = Interceptor { it.proceed(it.request()) }
    override val autenticador = Authenticator.NONE
}

/** Os serviços montados pelo [ModuloDaRede], falando com um MockWebServer em `/api/`. */
class ApiDeTeste : Closeable {
    val servidor = MockWebServer().apply { start() }
    val aviso = AvisoDeVersao()
    private val retrofit = ModuloDaRede.retrofit(
        servidor.url("/api/").toString(),
        ModuloDaRede.okHttp("2.0.0", aviso, SemAutenticacao),
        JsonDoPiggu,
    )
    val identidade = ModuloDaRede.identidade(retrofit)
    val financas = ModuloDaRede.financas(retrofit)
    val bancos = ModuloDaRede.bancos(retrofit)
    val estiloDeVida = ModuloDaRede.estiloDeVida(retrofit)
    val midia = ModuloDaRede.midia(retrofit)
    val recompensas = ModuloDaRede.recompensas(retrofit)

    fun responder(codigo: Int = 200, corpo: String = "", vararg cabecalhos: String) =
        servidor.enqueue(MockResponse(codigo, headersOf(*cabecalhos), corpo))

    fun pedido(): RecordedRequest = servidor.takeRequest()

    override fun close() = servidor.close()
}
