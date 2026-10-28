package br.com.piggu.feature.plano

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.model.InfoDoPlano
import br.com.piggu.core.model.JsonDoPiggu
import br.com.piggu.core.model.OrigemDaAssinatura
import br.com.piggu.core.model.Plano
import br.com.piggu.core.model.Precos
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeIdentidade
import br.com.piggu.core.testing.DIA_DAS_CAPTURAS
import br.com.piggu.core.testing.RedeDeTeste
import br.com.piggu.core.testing.RespostaFixa
import br.com.piggu.core.testing.RespostasFixas
import br.com.piggu.core.testing.esperar
import br.com.piggu.core.testing.redeDeTeste
import br.com.piggu.core.ui.FalhaAoCarregar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

private class GuardadoNaMemoria : RespostasGuardadasDao {
    val respostas = ConcurrentHashMap<String, RespostaGuardada>()
    override suspend fun ler(chave: String) = respostas[chave]
    override suspend fun guardar(resposta: RespostaGuardada) { respostas[resposta.chave] = resposta }
    override suspend fun apagarTudo() = respostas.clear()
}

/** O GRATUITO do plan.spec.ts, com os preços do app e o casal. */
internal val GRATUITO = InfoDoPlano(
    plano = Plano.GRATUITO,
    diasDeTeste = 7,
    assinaturaDisponivel = true,
    site = Precos("19,90", "199,00"),
    app = Precos("22,89", "228,85"),
    gratuito = listOf("Gastos"),
    premium = listOf("Assistente de IA"),
    planoFamilia = false,
    voceAssinou = false,
    familiaDisponivel = true,
    siteFamilia = Precos("39,99", "399,90"),
    appFamilia = Precos("45,99", "459,89"),
    pessoasNoFamilia = 5,
    casaTemAssinatura = false,
    planoCasal = false,
    casalDisponivel = true,
    siteCasal = Precos("29,90", "299,00"),
    appCasal = Precos("34,39", "343,85"),
)

internal val PREMIUM_PELO_SITE = GRATUITO.copy(
    plano = Plano.PREMIUM,
    premiumAte = "2026-11-08T00:00:00Z",
    origem = OrigemDaAssinatura.WEB,
    voceAssinou = true,
    casaTemAssinatura = true,
)

/** Premium (plan.ts e plan.spec.ts), com a API falsa e o `/billing/plan` que o teste mandar. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanoViewModelTest {
    @get:Rule val pasta = TemporaryFolder()
    private val loja = ViewModelStore()
    private val guardado = GuardadoNaMemoria()
    private val conectado = MutableStateFlow(true)
    private val cache = CacheDeRespostas(guardado, DIA_DAS_CAPTURAS)
    private lateinit var fixas: RespostasFixas
    private lateinit var rede: RedeDeTeste
    private var criados = 0

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())
    // Sem resetMain: resposta atrasada da API falsa (outra thread) iria para um Main desfeito e derrubava o teste seguinte no CI.

    @After fun devolver() = loja.clear()

    /** As respostas fixas do teste; mudam no meio dele (o plano depois do reembolso). */
    private val respostas = ConcurrentHashMap<String, RespostaFixa>()

    private fun responderPlano(json: String) {
        respostas["GET /api/billing/plan"] = RespostaFixa(200, json)
    }

    private fun responderPlano(plano: InfoDoPlano) = responderPlano(JsonDoPiggu.encodeToString(InfoDoPlano.serializer(), plano))

    /** Entra com a Ana, sem nada guardado; [plano] responde o `/billing/plan` (nulo: o da demonstração). */
    private suspend fun TestScope.entrar(plano: InfoDoPlano? = null) {
        respostas.clear()
        guardado.respostas.clear()
        plano?.let(::responderPlano)
        fixas = RespostasFixas(respostas)
        rede = redeDeTeste(pasta.newFolder(), fixas)
        rede.entrar()
    }

    private fun plano(identidade: ServicoDeIdentidade = rede.identidade): PlanoViewModel =
        ViewModelProvider.create(
            loja,
            viewModelFactory { initializer { PlanoViewModel(identidade, cache, rede.sessao, DIA_DAS_CAPTURAS, conectado) } },
        )["vm${criados++}", PlanoViewModel::class]

    /** Só o que não é entrar: nenhum checkout, portal ou compra sai desta tela sozinho. */
    private fun pedidos() = fixas.pedidos.filterNot { it.startsWith("POST /api/auth/") }

    @Test
    fun `gratuito marca o gratuito, mostra os precos do app e o em breve sem chamar nada`() = runTest {
        entrar(GRATUITO)
        val estado = plano().estado.esperar { it.info != null }
        assertEquals(QualPlano.GRATUITO, estado.seuPlano)
        assertTrue(estado.podeAssinar)
        assertEquals(Precos("22,89", "228,85"), estado.info!!.app)
        assertEquals(Precos("34,39", "343,85"), estado.info!!.appCasal)
        assertEquals(Precos("45,99", "459,89"), estado.info!!.appFamilia)
        assertNull(estado.guardadoEm)
        assertEquals(listOf("GET /api/billing/plan"), pedidos())
    }

    @Test
    fun `premium individual traz a data e nao oferece assinar`() = runTest {
        entrar(PREMIUM_PELO_SITE)
        val estado = plano().estado.esperar { it.info != null }
        assertEquals(QualPlano.PREMIUM, estado.seuPlano)
        assertEquals("2026-11-08T00:00:00Z", estado.info!!.premiumAte)
        assertFalse(estado.podeAssinar)
    }

    @Test
    fun `casal e familia marcam o proprio cartao, e a assinatura de outra pessoa da casa barra o assinar`() = runTest {
        entrar(PREMIUM_PELO_SITE.copy(planoCasal = true, voceAssinou = false))
        assertEquals(QualPlano.CASAL, plano().estado.esperar { it.info != null }.seuPlano)

        // A demonstração: a Ana assinou o família pelo site.
        entrar()
        val familia = plano().estado.esperar { it.info != null }
        assertEquals(QualPlano.FAMILIA, familia.seuPlano)
        assertNotNull(familia.info!!.premiumAte)

        entrar(GRATUITO.copy(casaTemAssinatura = true))
        val daCasa = plano().estado.esperar { it.info != null }
        assertEquals(QualPlano.GRATUITO, daCasa.seuPlano)
        assertFalse(daCasa.podeAssinar)
    }

    @Test
    fun `administrador sem plano nao tem cartao marcado nem assina`() {
        val estado = EstadoDoPlano(Carga.Pronto(GRATUITO), admin = true)
        assertNull(estado.seuPlano)
        assertFalse(estado.podeAssinar)
    }

    @Test
    fun `le a origem de cada loja, e a desconhecida nao quebra`() = runTest {
        for (origem in listOf(OrigemDaAssinatura.WEB, OrigemDaAssinatura.APP_STORE, OrigemDaAssinatura.PLAY_STORE)) {
            entrar(PREMIUM_PELO_SITE.copy(origem = origem))
            assertEquals(origem, plano().estado.esperar { it.info != null }.info!!.origem)
        }
        entrar()
        responderPlano(JsonDoPiggu.encodeToString(InfoDoPlano.serializer(), PREMIUM_PELO_SITE).replace("\"WEB\"", "\"OUTRA_LOJA\""))
        assertEquals(OrigemDaAssinatura.DESCONHECIDO, plano().estado.esperar { it.info != null }.info!!.origem)
    }

    @Test
    fun `reembolso so depois de confirmar, renova o token e recarrega`() = runTest {
        entrar(PREMIUM_PELO_SITE.copy(reembolsoAte = "2026-11-01T00:00:00Z"))
        respostas["POST /api/billing/refund"] = RespostaFixa(200, """{"reembolsado":true}""")
        val plano = plano()
        plano.estado.esperar { it.info != null }

        plano.pedirReembolso()
        assertTrue(plano.estado.value.confirmandoReembolso)
        plano.desistir()
        assertFalse(plano.estado.value.confirmandoReembolso)
        assertTrue(pedidos().none { it.startsWith("POST /api/billing") })

        plano.pedirReembolso()
        responderPlano(GRATUITO)
        plano.confirmarReembolso()
        val depois = plano.estado.esperar { it.aviso != null && it.seuPlano == QualPlano.GRATUITO }
        assertEquals(R.string.plano_reembolsado, depois.aviso)
        assertFalse(depois.ocupado)
        assertEquals(1, fixas.pedidos.count { it.startsWith("POST /api/auth/refresh") })
        assertEquals(listOf("GET /api/billing/plan", "POST /api/billing/refund {}", "GET /api/billing/plan"), pedidos())
    }

    @Test
    fun `reembolso recusado mostra o erro do servidor e libera o botao`() = runTest {
        // A demonstração recusa todo pagamento (422).
        entrar()
        val plano = plano()
        plano.estado.esperar { it.info != null }
        plano.estado.test {
            awaitItem()
            plano.pedirReembolso()
            awaitItem()
            plano.confirmarReembolso()
            assertTrue(awaitItem().ocupado)
            awaitItem().let {
                assertFalse(it.ocupado)
                assertEquals(MensagemDeErro.DoServidor("Pagamento indisponível na demonstração: esta conta de exemplo já é Premium."), it.erro)
                assertNull(it.aviso)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sem internet mostra o guardado com a hora, ou o erro quando nao ha nada`() = runTest {
        entrar(GRATUITO)
        val semRede = object : ServicoDeIdentidade by rede.identidade {
            override suspend fun plano(campanha: String?): InfoDoPlano = throw ErroDaApi.SemInternet(IOException("sem rede"))
        }
        conectado.value = false
        val vazio = plano(semRede).estado.esperar { it.plano is Carga.Falhou }
        assertEquals(FalhaAoCarregar.SemInternet, falhaDe((vazio.plano as Carga.Falhou).erro))
        assertFalse(vazio.conectado)

        conectado.value = true
        plano().estado.esperar { it.info != null }
        conectado.value = false
        val comGuardado = plano(semRede).estado.esperar { it.guardadoEm != null }
        assertEquals(QualPlano.GRATUITO, comGuardado.seuPlano)
        assertFalse(comGuardado.conectado)
    }

    @Test
    fun `erro do servidor pede tentar de novo`() = runTest {
        entrar(GRATUITO)
        var falhar = true
        val instavel = object : ServicoDeIdentidade by rede.identidade {
            override suspend fun plano(campanha: String?): InfoDoPlano =
                if (falhar) throw ErroDaApi.DoServidor(503, null, "Fora do ar.", emptyList(), null) else rede.identidade.plano(campanha)
        }
        val plano = plano(instavel)
        val falhou = plano.estado.esperar { it.plano is Carga.Falhou }
        assertEquals(FalhaAoCarregar.DoServidor, falhaDe((falhou.plano as Carga.Falhou).erro))
        falhar = false
        plano.tentarDeNovo()
        assertEquals(QualPlano.GRATUITO, plano.estado.esperar { it.info != null }.seuPlano)
    }
}
