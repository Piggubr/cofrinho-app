package br.com.piggu.core.network

import android.content.Context
import androidx.annotation.StringRes
import br.com.piggu.core.model.CampoInvalido
import br.com.piggu.core.model.CorpoDeErro
import br.com.piggu.core.model.JsonDoPiggu
import java.io.IOException
import java.lang.reflect.Type
import kotlinx.serialization.SerializationException
import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit

/** O que pode dar errado numa chamada à API. Os serviços Retrofit só lançam isto. */
sealed class ErroDaApi(mensagem: String?, causa: Throwable?) : Exception(mensagem, causa) {

    /** Sem rede, tempo esgotado ou servidor fora do ar antes de responder. */
    class SemInternet(causa: IOException) : ErroDaApi(causa.message, causa)

    /** O servidor respondeu algo que o app não entendeu (fora do contrato). */
    class RespostaInvalida(causa: Throwable) : ErroDaApi(causa.message, causa)

    /**
     * Resposta fora de 2xx. Decida pelo [status] e, num caso específico, pelo [codigo]
     * (API_SPEC, Erros), nunca pelo texto.
     */
    class DoServidor(
        val status: Int,
        val codigo: String?,
        /** O `erro` do corpo, já escrito para a tela; ou o corpo, quando veio em texto puro. */
        val mensagem: String?,
        /** Só em VALIDACAO: o erro de cada campo do formulário. */
        val campos: List<CampoInvalido>,
        /** O X-Request-Id que o gateway devolve, para achar a chamada no log. */
        val requestId: String?,
    ) : ErroDaApi("HTTP $status ${codigo.orEmpty()}".trim(), null)

    companion object {
        fun de(erro: Throwable): Throwable = when (erro) {
            is ErroDaApi -> erro
            is IOException -> SemInternet(erro)
            is SerializationException -> RespostaInvalida(erro)
            else -> erro
        }

        fun de(resposta: Response<*>): DoServidor {
            val texto = resposta.errorBody()?.use { it.string() }.orEmpty()
            val corpo = lerCorpoDeErro(texto)
            val html = resposta.headers()["Content-Type"]?.contains("html") == true
            return DoServidor(
                status = resposta.code(),
                codigo = corpo?.codigo,
                // Como o site: corpo em texto puro vai para a tela; página HTML do proxy, não.
                mensagem = corpo?.erro ?: texto.takeIf { corpo == null && !html && it.isNotBlank() },
                campos = corpo?.campos.orEmpty(),
                requestId = resposta.headers()["X-Request-Id"],
            )
        }
    }
}

internal fun lerCorpoDeErro(texto: String): CorpoDeErro? =
    try {
        JsonDoPiggu.decodeFromString(CorpoDeErro.serializer(), texto)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

/** O texto do erro para a tela: um do app (`strings.xml`) ou o que o servidor escreveu. */
sealed interface MensagemDeErro {
    data class DoApp(@param:StringRes val texto: Int) : MensagemDeErro

    data class DoServidor(val texto: String) : MensagemDeErro

    fun texto(contexto: Context): String = when (this) {
        is DoApp -> contexto.getString(texto)
        is DoServidor -> texto
    }
}

/**
 * Como o mensagem-de-erro.ts do site: o texto que o backend escreveu, o aviso de sem
 * conexão, ou o [padrao] quando o erro não traz mensagem.
 */
fun mensagemDeErro(
    erro: Throwable,
    @StringRes padrao: Int = R.string.erro_nao_foi_possivel_concluir,
): MensagemDeErro = when {
    erro is ErroDaApi.SemInternet -> MensagemDeErro.DoApp(R.string.erro_sem_conexao)
    erro is ErroDaApi.DoServidor && !erro.mensagem.isNullOrBlank() -> MensagemDeErro.DoServidor(erro.mensagem)
    else -> MensagemDeErro.DoApp(padrao)
}

/** Faz cada serviço Retrofit lançar [ErroDaApi] em vez de HttpException e IOException. */
internal object AdaptadorDeErros : CallAdapter.Factory() {
    override fun get(tipo: Type, anotacoes: Array<out Annotation>, retrofit: Retrofit): CallAdapter<*, *>? {
        if (getRawType(tipo) != Call::class.java) return null
        @Suppress("UNCHECKED_CAST")
        val proximo = retrofit.nextCallAdapter(this, tipo, anotacoes) as CallAdapter<Any, Call<Any>>
        return object : CallAdapter<Any, Call<Any>> {
            override fun responseType(): Type = proximo.responseType()

            override fun adapt(chamada: Call<Any>): Call<Any> = ChamadaComErroDaApi(proximo.adapt(chamada))
        }
    }
}

private class ChamadaComErroDaApi<T>(private val chamada: Call<T>) : Call<T> {
    override fun enqueue(callback: Callback<T>) = chamada.enqueue(object : Callback<T> {
        override fun onResponse(call: Call<T>, response: Response<T>) {
            if (response.isSuccessful) {
                callback.onResponse(this@ChamadaComErroDaApi, response)
            } else {
                callback.onFailure(this@ChamadaComErroDaApi, ErroDaApi.de(response))
            }
        }

        override fun onFailure(call: Call<T>, t: Throwable) =
            callback.onFailure(this@ChamadaComErroDaApi, ErroDaApi.de(t))
    })

    override fun execute(): Response<T> = chamada.execute()
    override fun clone(): Call<T> = ChamadaComErroDaApi(chamada.clone())
    override fun isExecuted() = chamada.isExecuted
    override fun cancel() = chamada.cancel()
    override fun isCanceled() = chamada.isCanceled
    override fun request(): Request = chamada.request()
    override fun timeout(): Timeout = chamada.timeout()
}
