package br.com.piggu.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.datastore.Cifra
import br.com.piggu.core.datastore.CofreDaSessao
import br.com.piggu.core.model.JsonDoPiggu
import br.com.piggu.core.network.AvisoDeVersao
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.ModuloDaRede
import br.com.piggu.core.network.ServicoDeIdentidade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val USUARIO = """{"id":"u1","email":"ana@piggu.com.br","nome":"Ana Souza","primeiroNome":"Ana","role":"TITULAR","ativo":true,"plano":"PREMIUM","familia":"f1"}"""

private fun tokens(acesso: String, refresh: String) =
    """{"accessToken":"$acesso","expiresIn":1800,"refreshToken":"$refresh","usuario":$USUARIO}"""

/** O cofre de verdade, sem o Keystore (que não existe na JVM): a "cifra" só inverte os bytes. */
private object CifraDeTeste : Cifra {
    override fun cifrar(claro: ByteArray) = claro.reversedArray()
    override fun decifrar(cifrado: ByteArray) = cifrado.reversedArray()
}

private class CacheNaMemoria : RespostasGuardadasDao {
    val guardadas = mutableMapOf<String, RespostaGuardada>()
    override suspend fun ler(chave: String) = guardadas[chave]
    override suspend fun guardar(resposta: RespostaGuardada) { guardadas[resposta.chave] = resposta }
    override suspend fun apagarTudo() = guardadas.clear()
}

/**
 * A sessão com a rede de verdade (ModuloDaRede) falando com um servidor falso que só aceita o
 * token da última renovação e troca o refresh a cada uso, como o identity.
 */
class SessaoTest {
    @get:Rule val pasta = TemporaryFolder()

    private val servidor = MockWebServer()
    private val renovacoes = AtomicInteger()
    @Volatile private var refreshValido = "r0"
    /** O que o refresh responde quando não é para dar certo (401, 500, sem rede). */
    @Volatile private var falhaDoRefresh: MockResponse? = null
    private val logouts = mutableListOf<String>()
    private val renovacaoComecou = CountDownLatch(1)

    init {
        servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val corpo = request.body?.utf8().orEmpty()
                return when (request.url.encodedPath) {
                    "/api/auth/entrar" -> MockResponse(200, body = tokens("a1", "r1")).also { refreshValido = "r1" }
                    "/api/auth/refresh" -> falhaDoRefresh ?: run {
                        if (!corpo.contains("\"$refreshValido\"")) return MockResponse(401, body = """{"erro":"Sessão expirada.","codigo":"NAO_AUTENTICADO"}""")
                        renovacaoComecou.countDown()
                        // Devagar, para as outras chamadas chegarem durante a renovação.
                        Thread.sleep(150)
                        val n = renovacoes.incrementAndGet()
                        refreshValido = "r-novo-$n"
                        MockResponse(200, body = tokens("novo-$n", refreshValido))
                    }
                    "/api/expenses" -> MockResponse(403, body = """{"erro":"Complete o cadastro.","codigo":"CADASTRO_PENDENTE"}""")
                    "/api/auth/logout" -> MockResponse(204).also { synchronized(logouts) { logouts += corpo } }
                    "/api/auth/me" ->
                        if (request.headers["Authorization"] == "Bearer novo-${renovacoes.get()}" && renovacoes.get() > 0) MockResponse(200, body = USUARIO)
                        else MockResponse(401, body = """{"erro":"Token vencido.","codigo":"NAO_AUTENTICADO"}""")
                    else -> MockResponse(404)
                }
            }
        }
        servidor.start()
    }

    @After
    fun fechar() = servidor.close()

    private val cache = CacheNaMemoria()

    private fun TestScope.novaSessao(
        cofre: CofreDaSessao = CofreDaSessao(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { pasta.root.resolve("sessao.preferences_pb") }),
            CifraDeTeste,
        ),
    ): Pair<Sessao, CofreDaSessao> {
        lateinit var identidade: ServicoDeIdentidade
        val sessao = Sessao({ identidade }, cofre, cache, backgroundScope)
        val retrofit = ModuloDaRede.retrofit(
            servidor.url("/api/").toString(),
            ModuloDaRede.okHttp("2.0.0", AvisoDeVersao(), AutenticacaoDaSessao(sessao)),
            JsonDoPiggu,
        )
        identidade = ModuloDaRede.identidade(retrofit)
        return sessao to cofre
    }

    private suspend fun <T> naRede(bloco: suspend () -> T) = withContext(Dispatchers.IO) { bloco() }

    @Test
    fun `chamadas simultaneas com o token vencido esperam a mesma renovacao`() = runTest {
        val (sessao, _) = novaSessao()
        naRede { sessao.entrarComSenha("ana@piggu.com.br", "senha", lembrar = true) }
        val identidade = sessaoServico(sessao)
        val respostas = naRede { (1..5).map { async(Dispatchers.IO) { identidade.meuPerfil() } }.awaitAll() }
        assertEquals(5, respostas.size)
        // Uma só: o backend troca o refresh a cada uso, e uma segunda invalidaria a primeira.
        assertEquals(1, renovacoes.get())
    }

    @Test
    fun `renovacao recusada encerra a sessao e apaga o que e da conta`() = runTest {
        val (sessao, cofre) = novaSessao()
        naRede { sessao.entrarComSenha("ana@piggu.com.br", "senha", lembrar = true) }
        cache.guardadas["resumo/2026-10"] = RespostaGuardada("resumo/2026-10", "{}", 1)
        falhaDoRefresh = MockResponse(401, body = """{"erro":"Sessão expirada.","codigo":"NAO_AUTENTICADO"}""")
        try {
            naRede { sessaoServico(sessao).meuPerfil() }
            fail("o 401 segue para quem chamou")
        } catch (erro: ErroDaApi.DoServidor) {
            assertEquals(401, erro.status)
        }
        esperar { sessao.estado.value == EstadoDaSessao.Deslogado }
        assertNull(cofre.refresh())
        assertTrue(cache.guardadas.isEmpty())
    }

    @Test
    fun `servidor fora ou 429 na renovacao so repassam o erro`() = runTest {
        val (sessao, cofre) = novaSessao()
        naRede { sessao.entrarComSenha("ana@piggu.com.br", "senha", lembrar = true) }
        for (codigo in listOf(500, 429, 503)) {
            falhaDoRefresh = MockResponse(codigo, body = """{"erro":"Fora do ar.","codigo":"X"}""")
            runCatching { naRede { sessaoServico(sessao).meuPerfil() } }
            assertTrue(sessao.estado.value is EstadoDaSessao.Logado)
        }
        assertEquals("r1", cofre.refresh())
    }

    @Test
    fun `com o perfil guardado abre logado na hora, mesmo sem o servidor`() = runTest {
        val (primeira, cofre) = novaSessao()
        naRede { primeira.entrarComSenha("ana@piggu.com.br", "senha", lembrar = true) }
        servidor.close()
        // O mesmo disco, como ao abrir o app de novo.
        val (sessao, _) = novaSessao(cofre)
        naRede { sessao.restaurar() }
        val estado = sessao.estado.value
        assertTrue(estado is EstadoDaSessao.Logado)
        assertEquals("Ana", (estado as EstadoDaSessao.Logado).usuario.primeiroNome)
    }

    @Test
    fun `sem continuar conectado o refresh nao vai para o disco`() = runTest {
        val (sessao, cofre) = novaSessao()
        naRede { sessao.entrarComSenha("ana@piggu.com.br", "senha", lembrar = false) }
        assertTrue(sessao.estado.value is EstadoDaSessao.Logado)
        assertNull(cofre.refresh())
        assertNull(cofre.perfil())
    }

    @Test
    fun `sair avisa o servidor com o refresh e apaga tudo`() = runTest {
        val (sessao, cofre) = novaSessao()
        naRede { sessao.entrarComSenha("ana@piggu.com.br", "senha", lembrar = true) }
        cache.guardadas["categorias"] = RespostaGuardada("categorias", "{}", 1)
        naRede { sessao.sair() }
        assertEquals(listOf("""{"refreshToken":"r1"}"""), logouts)
        assertEquals(EstadoDaSessao.Deslogado, sessao.estado.value)
        assertNull(cofre.refresh())
        assertTrue(cache.guardadas.isEmpty())
    }

    @Test
    fun `sair durante uma renovacao espera ela e nao volta a entrar`() = runTest {
        val (sessao, cofre) = novaSessao()
        naRede { sessao.entrarComSenha("ana@piggu.com.br", "senha", lembrar = true) }
        // O /me recusa o token do login: a chamada renova, e a pessoa toca em Sair no meio.
        val chamada = async(Dispatchers.IO) { runCatching { sessaoServico(sessao).meuPerfil() } }
        naRede { assertTrue(renovacaoComecou.await(30, TimeUnit.SECONDS)) }
        naRede { sessao.sair() }
        chamada.await()
        assertEquals(EstadoDaSessao.Deslogado, sessao.estado.value)
        // O refresh que vale no servidor, para a sessão nova não ficar aberta por 30 dias.
        assertEquals(listOf("""{"refreshToken":"r-novo-1"}"""), logouts)
        assertNull(cofre.refresh())
    }

    @Test
    fun `403 CADASTRO_PENDENTE leva ao Sobre voce`() = runTest {
        val (sessao, _) = novaSessao()
        naRede { sessao.entrarComSenha("ana@piggu.com.br", "senha", lembrar = true) }
        val retrofit = ModuloDaRede.retrofit(servidor.url("/api/").toString(), ModuloDaRede.okHttp("2.0.0", AvisoDeVersao(), AutenticacaoDaSessao(sessao)), JsonDoPiggu)
        runCatching { naRede { ModuloDaRede.financas(retrofit).listarGastos("2026-10") } }
        assertEquals(Pendencia.SobreVoce, (sessao.estado.value as EstadoDaSessao.Logado).usuario.pendencia)
    }

    @Test
    fun `sem refresh guardado abre deslogado`() = runTest {
        val (sessao, _) = novaSessao()
        naRede { sessao.restaurar() }
        assertEquals(EstadoDaSessao.Deslogado, sessao.estado.value)
    }

    /** O serviço montado com esta sessão (o mesmo que a sessão usa). */
    private fun sessaoServico(sessao: Sessao): ServicoDeIdentidade {
        val campo = Sessao::class.java.getDeclaredField("identidade").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        return (campo.get(sessao) as dagger.Lazy<ServicoDeIdentidade>).get()
    }

    private suspend fun esperar(condicao: () -> Boolean) = naRede {
        repeat(1_500) { if (condicao()) return@naRede; Thread.sleep(20) } // até 30 s: o CI do GitHub é lento
        fail("não aconteceu a tempo")
    }
}
