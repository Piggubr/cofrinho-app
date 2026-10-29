package br.com.piggu.feature.login

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
import br.com.piggu.core.testing.ApiDaDemo
import br.com.piggu.core.testing.RespostasFixas
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
fun TestScope.sessaoDeTeste(pasta: File, fixas: RespostasFixas = RespostasFixas()): Sessao {
    val cofre = CofreDaSessao(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { pasta.resolve("s.preferences_pb") }), SemCifra)
    lateinit var identidade: ServicoDeIdentidade
    val sessao = Sessao({ identidade }, cofre, SemCache, backgroundScope)
    val cliente = ModuloDaRede.okHttp("2.0.0", AvisoDeVersao(), AutenticacaoDaSessao(sessao), linkedSetOf(fixas, ApiDaDemo()))
    identidade = ModuloDaRede.identidade(ModuloDaRede.retrofit(URL_DA_API, cliente, JsonDoPiggu))
    return sessao
}

/** A API falsa responde noutra thread (OkHttp): espera fora do relógio virtual do teste. */
suspend fun <T> StateFlow<T>.esperar(condicao: (T) -> Boolean): T =
    withContext(Dispatchers.Default) { withTimeout(5_000) { first(condicao) } }
