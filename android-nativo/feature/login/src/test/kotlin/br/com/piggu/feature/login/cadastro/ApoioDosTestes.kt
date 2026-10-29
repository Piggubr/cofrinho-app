package br.com.piggu.feature.login.cadastro

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.ReceiveTurbine
import br.com.piggu.core.data.AutenticacaoDaSessao
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.datastore.Cifra
import br.com.piggu.core.datastore.CofreDaSessao
import br.com.piggu.core.model.JsonDoPiggu
import br.com.piggu.core.model.Usuario
import br.com.piggu.core.network.AvisoDeVersao
import br.com.piggu.core.network.ModuloDaRede
import br.com.piggu.core.network.ServicoDeIdentidade
import br.com.piggu.core.network.URL_DA_API
import br.com.piggu.core.testing.RespostaFixa
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.TestScope
import okhttp3.Interceptor

/** Espera, pelo Turbine, o primeiro estado que cumpre a [condicao]. */
suspend fun <T> ReceiveTurbine<T>.ate(condicao: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (condicao(item)) return item
    }
}

/** Um perfil do identity em JSON, para as [RespostaFixa] e para abrir a sessão. */
fun usuarioJson(extras: String = "") =
    """{"id":"u1","email":"tita@piggu.test","nome":"Titular Teste","primeiroNome":"Titular","role":"TITULAR",""" +
        """"ativo":true,"plano":"GRATUITO","familia":"f1"$extras}"""

fun usuario(extras: String = ""): Usuario = JsonDoPiggu.decodeFromString(Usuario.serializer(), usuarioJson(extras))

/** O 400 VALIDACAO do backend com um campo. */
fun validacao(campo: String, mensagem: String) = RespostaFixa(
    400,
    """{"erro":"$mensagem","codigo":"VALIDACAO","campos":[{"campo":"$campo","mensagem":"$mensagem"}],"momento":"2026-10-28T13:00:00Z"}""",
)

private object SemCifra : Cifra {
    override fun cifrar(claro: ByteArray) = claro
    override fun decifrar(cifrado: ByteArray) = cifrado
}

private object SemCache : RespostasGuardadasDao {
    override suspend fun ler(chave: String): RespostaGuardada? = null
    override suspend fun guardar(resposta: RespostaGuardada) = Unit
    override suspend fun apagarTudo() = Unit
}

/** A sessão de verdade sem rede nenhuma: toda chamada falha como sem sinal. */
fun TestScope.sessaoSemInternet(pasta: File): Sessao {
    val cofre = CofreDaSessao(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { pasta.resolve("s.preferences_pb") }), SemCifra)
    lateinit var identidade: ServicoDeIdentidade
    val sessao = Sessao({ identidade }, cofre, SemCache, backgroundScope)
    val semSinal = Interceptor { throw IOException("sem sinal") }
    val cliente = ModuloDaRede.okHttp("2.0.0", AvisoDeVersao(), AutenticacaoDaSessao(sessao), linkedSetOf(semSinal))
    identidade = ModuloDaRede.identidade(ModuloDaRede.retrofit(URL_DA_API, cliente, JsonDoPiggu))
    return sessao
}
