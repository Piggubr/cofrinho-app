package br.com.piggu.core.testing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import br.com.piggu.core.data.AutenticacaoDaSessao
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.datastore.Cifra
import br.com.piggu.core.datastore.CofreDaSessao
import br.com.piggu.core.model.JsonDoPiggu
import br.com.piggu.core.network.AvisoDeVersao
import br.com.piggu.core.network.ModuloDaRede
import br.com.piggu.core.network.ServicoDeIdentidade
import br.com.piggu.core.network.URL_DA_API
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

private object SemCifra : Cifra {
    override fun cifrar(claro: ByteArray) = claro
    override fun decifrar(cifrado: ByteArray) = cifrado
}

private object SemCache : RespostasGuardadasDao {
    override suspend fun ler(chave: String): RespostaGuardada? = null
    override suspend fun guardar(resposta: RespostaGuardada) = Unit
    override suspend fun apagarTudo() = Unit
}

/**
 * A sessão de verdade, com a rede de verdade, respondida pela [ApiDaDemo]. As [fixas] vêm antes
 * e respondem o que a demo nunca responde (senha errada, termos pendentes...).
 */
fun TestScope.sessaoDeTeste(pasta: File, fixas: RespostasFixas = RespostasFixas()): Sessao = redeDeTeste(pasta, fixas).sessao

/** A sessão e o Retrofit montado com ela, para os serviços das outras telas (finanças, estilo de vida...). */
class RedeDeTeste(val sessao: Sessao, val retrofit: retrofit2.Retrofit) {
    val financas get() = ModuloDaRede.financas(retrofit)
    val identidade get() = ModuloDaRede.identidade(retrofit)
    val estiloDeVida get() = ModuloDaRede.estiloDeVida(retrofit)
    val midia get() = ModuloDaRede.midia(retrofit)
    val bancos get() = ModuloDaRede.bancos(retrofit)
    val recompensas get() = ModuloDaRede.recompensas(retrofit)

    /** Entra com a conta da demonstração (Ana, titular com Premium). */
    suspend fun entrar() = sessao.entrarComSenha("ana@demo.piggu.app", "senha123", lembrar = false)
}

fun TestScope.redeDeTeste(pasta: File, fixas: RespostasFixas = RespostasFixas(), demo: ApiDaDemo = ApiDaDemo(DIA_DAS_CAPTURAS)): RedeDeTeste {
    val cofre = CofreDaSessao(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { pasta.resolve("s.preferences_pb") }), SemCifra)
    lateinit var identidade: ServicoDeIdentidade
    val sessao = Sessao({ identidade }, cofre, SemCache, backgroundScope)
    val cliente = ModuloDaRede.okHttp("2.0.0", AvisoDeVersao(), AutenticacaoDaSessao(sessao), linkedSetOf(fixas, demo))
    val retrofit = ModuloDaRede.retrofit(URL_DA_API, cliente, JsonDoPiggu)
    identidade = ModuloDaRede.identidade(retrofit)
    return RedeDeTeste(sessao, retrofit)
}

/** O dia que os testes e as capturas fixam: 28/10/2026, 10h em São Paulo (os dados da demo contam com ele). */
val DIA_DAS_CAPTURAS: java.time.Clock = java.time.ZoneId.of("America/Sao_Paulo").let { zona ->
    java.time.Clock.fixed(java.time.ZonedDateTime.of(2026, 10, 28, 10, 0, 0, 0, zona).toInstant(), zona)
}

/** A API falsa responde noutra thread (OkHttp): espera fora do relógio virtual do teste. */
suspend fun <T> StateFlow<T>.esperar(condicao: (T) -> Boolean): T =
    withContext(Dispatchers.Default) { withTimeout(5_000) { first(condicao) } }
