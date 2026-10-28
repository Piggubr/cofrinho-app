package br.com.piggu.feature.plano

import android.app.Activity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import br.com.piggu.core.testing.TOKEN_DE_OUTRA_CONTA
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
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
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

/** Premium (plan.ts e plan.spec.ts), com a API falsa, o `/billing/plan` que o teste mandar e a loja de mentira. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
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

    private fun plano(identidade: ServicoDeIdentidade = rede.identidade, googlePlay: LojaDoGooglePlay = LojaDeMentira()): PlanoViewModel =
        ViewModelProvider.create(
            loja,
            viewModelFactory { initializer { PlanoViewModel(identidade, cache, rede.sessao, DIA_DAS_CAPTURAS, googlePlay, conectado) } },
        )["vm${criados++}", PlanoViewModel::class]

    /** A folha do Google Play precisa de uma Activity; a loja de mentira não a usa. */
    private val atividade: Activity by lazy { Robolectric.buildActivity(Activity::class.java).get() }

    private fun compras() = pedidos().filter { it.startsWith("POST /api/billing/google-play/purchases") }

    /** Só o que não é entrar: nenhum checkout, portal ou compra sai desta tela sozinho. */
    private fun pedidos() = fixas.pedidos.filterNot { it.startsWith("POST /api/auth/") }

    @Test
    fun `gratuito marca o gratuito, pode assinar e nao chama nada sozinho`() = runTest {
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
        plano.pedirReembolso()
        plano.confirmarReembolso()
        val recusado = plano.estado.esperar { it.erro != null }
        assertFalse(recusado.ocupado)
        assertEquals(MensagemDeErro.DoServidor("Pagamento indisponível na demonstração: esta conta de exemplo já é Premium."), recusado.erro)
        assertNull(recusado.aviso)
    }

    @Test
    fun `compra ok so vira Premium com a resposta do backend e renova o token`() = runTest {
        entrar(GRATUITO)
        val googlePlay = LojaDeMentira(mapOf(PREMIUM_MENSAL to "R$ 22,89", PREMIUM_ANUAL to "R$ 228,85"))
        val plano = plano(googlePlay = googlePlay)
        val antes = plano.estado.esperar { it.info != null && it.precosDaLoja.isNotEmpty() }
        assertEquals("R$ 22,89", antes.precosDaLoja[PREMIUM_MENSAL])

        plano.assinar(atividade, PREMIUM_MENSAL)
        val depois = plano.estado.esperar { it.aviso != null }
        assertEquals(R.string.plano_assinado, depois.aviso)
        assertEquals(QualPlano.PREMIUM, depois.seuPlano)
        assertEquals(OrigemDaAssinatura.PLAY_STORE, depois.info!!.origem)
        assertEquals(PREMIUM_MENSAL, depois.produtoDoPlay)
        assertFalse(depois.ocupado)
        assertEquals(
            listOf("""POST /api/billing/google-play/purchases {"produto":"premium_mensal","purchaseToken":"token-de-mentira-premium_mensal"}"""),
            compras(),
        )
        assertEquals(1, fixas.pedidos.count { it.startsWith("POST /api/auth/refresh") })
    }

    @Test
    fun `compra de outra conta do Piggu mostra o 409 e continua gratuito`() = runTest {
        entrar(GRATUITO)
        val googlePlay = LojaDeMentira { ResultadoDaCompra.Comprada(CompraDoGooglePlay(it, TOKEN_DE_OUTRA_CONTA)) }
        val plano = plano(googlePlay = googlePlay)
        plano.estado.esperar { it.info != null }
        plano.assinar(atividade, PREMIUM_ANUAL)
        val recusada = plano.estado.esperar { it.erro != null }
        assertEquals(MensagemDeErro.DoApp(R.string.plano_outra_conta), recusada.erro)
        assertEquals(QualPlano.GRATUITO, recusada.seuPlano)
        assertNull(recusada.aviso)
        assertFalse(recusada.ocupado)
    }

    @Test
    fun `pagamento pendente avisa sem chamar o backend, e cancelar nao diz nada`() = runTest {
        entrar(GRATUITO)
        var resultado: ResultadoDaCompra = ResultadoDaCompra.Pendente
        val plano = plano(googlePlay = LojaDeMentira { resultado })
        plano.estado.esperar { it.info != null }

        plano.assinar(atividade, PREMIUM_MENSAL)
        val pendente = plano.estado.esperar { it.aviso != null }
        assertEquals(R.string.plano_pagamento_pendente, pendente.aviso)
        assertEquals(QualPlano.GRATUITO, pendente.seuPlano)

        resultado = ResultadoDaCompra.Cancelada
        plano.assinar(atividade, PREMIUM_MENSAL)
        val cancelada = plano.estado.esperar { !it.ocupado && it.aviso == null }
        assertNull(cancelada.erro)
        assertTrue(compras().isEmpty())
    }

    @Test
    fun `restaurar manda cada assinatura ativa, ou diz que esta conta do Google nao tem`() = runTest {
        entrar(GRATUITO)
        val googlePlay = LojaDeMentira()
        val plano = plano(googlePlay = googlePlay)
        plano.estado.esperar { it.info != null }
        plano.restaurar()
        assertEquals(R.string.plano_nada_a_restaurar, plano.estado.esperar { it.aviso != null }.aviso)

        // Já reconhecida (o backend recebeu em outro aparelho): ao abrir não vai, no restaurar vai.
        googlePlay.compradas += CompraDoGooglePlay(PREMIUM_ANUAL, "token-anual", reconhecida = true)
        plano.restaurar()
        val restaurado = plano.estado.esperar { it.aviso == R.string.plano_restaurado }
        assertEquals(QualPlano.PREMIUM, restaurado.seuPlano)
        assertEquals(listOf("""POST /api/billing/google-play/purchases {"produto":"premium_anual","purchaseToken":"token-anual"}"""), compras())
    }

    @Test
    fun `premium de outra origem nao oferece comprar nem manda nada`() = runTest {
        for (origem in listOf(OrigemDaAssinatura.WEB, OrigemDaAssinatura.APP_STORE)) {
            entrar(PREMIUM_PELO_SITE.copy(origem = origem))
            val estado = plano().estado.esperar { it.info != null }
            assertFalse(estado.podeAssinar)
            assertTrue(compras().isEmpty())
        }
    }

    @Test
    fun `a compra que nao chegou ao backend vai sozinha ao abrir, sem aviso`() = runTest {
        entrar(GRATUITO)
        val googlePlay = LojaDeMentira().apply {
            compradas += CompraDoGooglePlay(PREMIUM_MENSAL, "token-sem-internet")
            compradas += CompraDoGooglePlay(PREMIUM_ANUAL, "token-antigo", reconhecida = true)
        }
        val aberto = plano(googlePlay = googlePlay).estado.esperar { it.seuPlano == QualPlano.PREMIUM }
        assertEquals(OrigemDaAssinatura.PLAY_STORE, aberto.info!!.origem)
        assertNull(aberto.aviso)
        assertNull(aberto.erro)
        assertEquals(listOf("""POST /api/billing/google-play/purchases {"produto":"premium_mensal","purchaseToken":"token-sem-internet"}"""), compras())
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
