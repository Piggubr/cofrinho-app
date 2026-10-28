package br.com.piggu.feature.painel

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.Lancamentos
import br.com.piggu.core.data.ListasParaLancar
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.datastore.PainelDoAparelho
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.testing.DIA_DAS_CAPTURAS
import br.com.piggu.core.testing.RespostaFixa
import br.com.piggu.core.testing.RespostasFixas
import br.com.piggu.core.testing.esperar
import br.com.piggu.core.testing.redeDeTeste
import br.com.piggu.core.ui.Visao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import br.com.piggu.core.designsystem.PigguTheme
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

private class CacheNaMemoria : RespostasGuardadasDao {
    val guardadas: MutableMap<String, RespostaGuardada> = ConcurrentHashMap()
    override suspend fun ler(chave: String) = guardadas[chave]
    override suspend fun guardar(resposta: RespostaGuardada) {
        guardadas[resposta.chave] = resposta
    }
    override suspend fun apagarTudo() = guardadas.clear()
}

private fun membro(id: String, nome: String, role: String, plano: String = "PREMIUM") = RespostaFixa(
    200,
    """{"accessToken":"a1","expiresIn":1800,"refreshToken":"r1","usuario":{"id":"$id","email":"x@demo.piggu.app","nome":"$nome Souza",""" +
        """"primeiroNome":"$nome","role":"$role","ativo":true,"plano":"$plano","familia":"familia-demo"}}""",
)

/** O Painel com a sessão e a rede de verdade, respondidas pela API da demonstração (Ana, Premium, com o Beto). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class PainelViewModelTest {
    @get:Rule val pasta = TemporaryFolder()
    @get:Rule val compose = createComposeRule()

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())
    // Sem resetMain: resposta atrasada da API falsa (outra thread) iria para um Main desfeito e derrubava o teste seguinte no CI.
    private class Painel(val viewModel: PainelViewModel, val fixas: RespostasFixas, val cache: CacheNaMemoria, val aparelho: PainelDoAparelho, val lancamentos: Lancamentos) {
        val estado get() = viewModel.estado.value
    }

    private suspend fun TestScope.abrir(fixas: RespostasFixas = RespostasFixas(), aparelho: PainelDoAparelho? = null): Painel {
        val rede = redeDeTeste(pasta.root, fixas)
        rede.entrar()
        val guardadas = CacheNaMemoria()
        val cache = CacheDeRespostas(guardadas, DIA_DAS_CAPTURAS)
        val doAparelho = aparelho ?: PainelDoAparelho(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { pasta.root.resolve("painel.preferences_pb") }))
        val lancamentos = Lancamentos()
        val viewModel = PainelViewModel(
            rede.sessao, cache, ListasParaLancar(cache, rede.financas, rede.identidade), rede.financas, rede.bancos, rede.estiloDeVida,
            rede.identidade, doAparelho, lancamentos, MonitorDeRede(ApplicationProvider.getApplicationContext(), backgroundScope), DIA_DAS_CAPTURAS,
        )
        return Painel(viewModel, fixas, guardadas, doAparelho, lancamentos)
    }

    /** Tudo o que o Painel busca ao abrir já chegou. */
    private suspend fun Painel.pronto(condicao: (EstadoDoPainel) -> Boolean = { true }) = viewModel.estado.esperar {
        it.carga is Carga.Pronto && it.resumo != null && it.objetivos != null && it.notas != null && it.bancos != null && it.membros.isNotEmpty() && condicao(it)
    }

    @Test
    fun `carrega o mes com o nucleo, o resumo e os cartoes`() = runTest {
        val painel = abrir()
        // Os avisos vêm num pedido à parte; na demo sempre há um (a Academia vencida em 25/10).
        val estado = painel.pronto { it.metasDoMes != null && !it.avisos.vazio }
        assertEquals("2026-10", estado.mes)
        assertEquals("Ana", estado.primeiroNome)
        assertTrue(estado.moraComAlguem)
        assertTrue(estado.gastoNoMes.signum() > 0)
        assertTrue(estado.porCategoria.isNotEmpty())
        assertEquals(0, estado.gastoNoMes.compareTo(estado.porCategoria.fold(BigDecimal.ZERO) { s, c -> s + c.total }))
        assertEquals(listOf(IdDoBloco.Resumo, IdDoBloco.Avisos, IdDoBloco.Mes, IdDoBloco.Cofrinho, IdDoBloco.Objetivos), estado.cartoes)
        // Open Finance ligado na demo: o cartão dos bancos vai junto do "mes".
        assertEquals(IdDoBloco.Mes, estado.bancosJuntoDe)
        // O núcleo e o resumo ficaram guardados para abrir sem internet.
        assertTrue(painel.cache.guardadas.keys.containsAll(listOf("painel/2026-10", "resumo/2026-10")))
    }

    @Test
    fun `trocar o mes e a visao busca de novo, com a chave da visao`() = runTest {
        val painel = abrir()
        val estado = painel.viewModel.estado
        estado.esperar { it.carga is Carga.Pronto }
        painel.viewModel.trocarVisao(Visao.Compartilhado)
        assertEquals(Visao.Compartilhado, estado.esperar { it.visao == Visao.Compartilhado }.visao)
        estado.esperar { it.carga is Carga.Pronto && "painel/2026-10/casal" in painel.cache.guardadas }
        painel.viewModel.mudarMes("2026-09")
        estado.esperar { it.mes == "2026-09" && it.carga is Carga.Pronto && "painel/2026-09/casal" in painel.cache.guardadas }
        assertTrue("resumo/2026-10/casal" in painel.cache.guardadas)
    }

    @Test
    fun `o membro nao ve os gastos, a meta nem o guia`() = runTest {
        val painel = abrir(RespostasFixas("POST /api/auth/entrar" to membro("u-beto", "Beto", "MEMBRO")))
        val estado = painel.viewModel.estado.esperar { it.carga is Carga.Pronto && it.ehMembro }
        assertFalse(estado.cartoes.any { it in listOf(IdDoBloco.Mes, IdDoBloco.Categorias, IdDoBloco.Resumo, IdDoBloco.Objetivos) })
        assertTrue(IdDoBloco.Cofrinho in estado.cartoes)
        assertFalse(estado.mostraGuia)
        // Nem ligando no organizar.
        painel.viewModel.alternarBloco(IdDoBloco.Categorias)
        val ligado = painel.viewModel.estado.esperar { e -> e.blocos.first { it.id == "categorias" }.visivel }
        assertFalse(IdDoBloco.Categorias in ligado.cartoes)
    }

    @Test
    fun `cria objetivo e manda so o que a pessoa pode escolher`() = runTest {
        val painel = abrir()
        painel.pronto()
        painel.viewModel.novoObjetivo()
        painel.viewModel.salvarObjetivo()
        assertEquals(MensagemDeErro.DoApp(R.string.objetivo_sem_nome), painel.estado.erros[IdDoBloco.Objetivos])

        painel.viewModel.mudarFormulario(FormularioDeObjetivo(nome = " Casa ", alvo = BigDecimal("1000.00"), prazo = "2027-01-31"))
        painel.viewModel.salvarObjetivo()
        val estado = painel.viewModel.estado.esperar { s -> s.formulario == null && s.objetivos.orEmpty().any { it.nome == "Casa" } }
        assertTrue(estado.erros.isEmpty() || IdDoBloco.Objetivos !in estado.erros)
        val pedido = painel.fixas.pedidos.last { it.startsWith("POST /api/piggy-bank/goals") }
        assertTrue(pedido, pedido.contains(""""nome":"Casa"""") && pedido.contains(""""prazo":"2027-01-31"""") && pedido.contains(""""compartilhado":false"""))
    }

    @Test
    fun `no gratuito o segundo objetivo e recusado e vira o convite do Premium`() = runTest {
        val fixas = RespostasFixas(
            "POST /api/auth/entrar" to membro("u-ana", "Ana", "TITULAR", plano = "GRATUITO"),
            "POST /api/piggy-bank/goals" to RespostasFixas.erro(403, "PLANO_PREMIUM", "Mais de um cofrinho por objetivo faz parte do Piggu Premium."),
        )
        val painel = abrir(fixas)
        // A Ana já tem a "Viagem de férias": no gratuito o botão de criar dá lugar ao convite.
        assertFalse(painel.pronto().podeCriarObjetivo)

        // Se o app achar que pode (o objetivo ainda não chegou, por exemplo), o backend barra.
        painel.viewModel.novoObjetivo()
        painel.viewModel.mudarFormulario(FormularioDeObjetivo(nome = "Sofá", alvo = BigDecimal("3000.00")))
        painel.viewModel.salvarObjetivo()
        val estado = painel.viewModel.estado.esperar { Acao.Objetivo !in it.ocupado && it.limiteDoGratuito }
        assertEquals(null, estado.formulario)
        assertFalse(estado.podeCriarObjetivo)
        assertEquals(MensagemDeErro.DoServidor("Mais de um cofrinho por objetivo faz parte do Piggu Premium."), estado.erros[IdDoBloco.Objetivos])
    }

    @Test
    fun `depositar no cofrinho e no objetivo, e excluir`() = runTest {
        val painel = abrir()
        val antes = painel.pronto()
        val cofrinho = (antes.carga as Carga.Pronto).valor.cofrinho

        painel.viewModel.depositar()
        assertEquals(MensagemDeErro.DoApp(R.string.deposito_invalido), painel.estado.erros[IdDoBloco.Cofrinho])

        painel.viewModel.mudarDeposito(BigDecimal("25.00"))
        painel.viewModel.depositar()
        val depois = painel.viewModel.estado.esperar { (it.carga as? Carga.Pronto)?.valor?.cofrinho?.totalDepositos?.compareTo(cofrinho.totalDepositos + BigDecimal("25.00")) == 0 }
        assertEquals(null, depois.deposito)
        assertTrue(painel.fixas.pedidos.any { it.startsWith("POST /api/piggy-bank/deposits") && it.contains(""""data":"2026-10-28"""") })

        val objetivo = depois.objetivos!!.first()
        painel.viewModel.mudarValorDoObjetivo(objetivo.id, BigDecimal("100.00"))
        painel.viewModel.depositarNoObjetivo(objetivo.id)
        painel.viewModel.estado.esperar { s -> s.objetivos!!.first { it.id == objetivo.id }.guardado.compareTo(objetivo.guardado + BigDecimal("100.00")) == 0 }
        assertTrue(painel.fixas.pedidos.any { it.contains(""""objetivo":"${objetivo.id}"""") })

        val deposito = (painel.estado.carga as Carga.Pronto).valor.cofrinho.depositos.first()
        painel.viewModel.excluirDeposito(deposito.id)
        painel.viewModel.estado.esperar { s -> Acao.Deposito !in s.ocupado && (s.carga as? Carga.Pronto)?.valor?.cofrinho?.depositos?.none { it.id == deposito.id } == true }

        // Só quem criou apaga; o que estava guardado nele volta para o cofrinho geral.
        assertTrue(painel.estado.ehMeu(objetivo.usuario))
        painel.viewModel.excluirObjetivo(objetivo.id)
        painel.viewModel.estado.esperar { s -> s.objetivos!!.none { it.id == objetivo.id } }
    }

    @Test
    fun `pular o guia fica no aparelho com o JSON do site`() = runTest {
        // Conta nova: sem limite e sem gasto, o guia aparece.
        val fixas = RespostasFixas(
            "GET /api/monthly-goals" to RespostaFixa(200, "{}"),
            "GET /api/expenses" to RespostaFixa(200, "[]"),
            "GET /api/piggy-bank" to RespostaFixa(200, """{"depositos":[],"totalDepositos":0,"totalGastos":0,"saldo":0}"""),
        )
        val painel = abrir(fixas)
        val estado = painel.viewModel.estado.esperar { it.carga is Carga.Pronto }
        assertTrue(estado.mostraGuia)
        assertEquals(0, estado.passosFeitos)

        painel.viewModel.pularGuia()
        assertFalse(painel.viewModel.estado.esperar { it.guia.fechado == true }.mostraGuia)
        assertEquals("""{"fechado":true}""", painel.aparelho.guia("u-ana").first { it != null })
    }

    @Test
    fun `organizar grava a ordem na chave do site`() = runTest {
        val painel = abrir()
        painel.viewModel.moverBloco(IdDoBloco.Cofrinho, -1)
        painel.viewModel.alternarBloco(IdDoBloco.Notas)
        val json = painel.aparelho.ordem("u-ana").first { it?.contains(""""id":"notas","visivel":true""") == true }
        assertEquals(painel.estado.blocos, PainelOrganizado.ordemValida(json))
        assertEquals(listOf("resumo", "avisos", "cofrinho", "mes"), painel.estado.blocos.take(4).map { it.id })
        painel.viewModel.restaurarPainel()
        assertEquals(PainelOrganizado.padrao, painel.estado.blocos)
    }

    @Test
    fun `algo lancado pelo + recarrega o Painel`() = runTest {
        val painel = abrir()
        painel.pronto()
        val antes = painel.fixas.pedidos.count { it == "GET /api/piggy-bank" }
        painel.lancamentos.avisar()
        // A recarga traz os mesmos dados, e o StateFlow não emite um estado igual: espera o pedido.
        withContext(Dispatchers.Default) {
            withTimeout(30_000) { while (painel.fixas.pedidos.count { it == "GET /api/piggy-bank" } <= antes) delay(20) }
        }
        painel.viewModel.estado.esperar { it.carga is Carga.Pronto }
        assertNotNull(painel.estado.resumo)
    }

    /** A tela sem o ViewModel desenha todos os cartões e o organizar (as capturas ficam para quem integra). */
    @Test
    fun `desenha todos os cartoes e o organizar`() {
        lateinit var carregado: EstadoDoPainel
        runTest { carregado = abrir().pronto { it.metasDoMes != null && it.girlsMath != null } }
        val estado = carregado.copy(blocos = PainelOrganizado.padrao.map { it.copy(visivel = true) }, notaNova = NotaNova(), formulario = FormularioDeObjetivo())
        compose.setContent { PigguTheme { TelaDoPainel(estado, EventosDoPainel(), {}, {}) } }
        compose.onNodeWithText("Olá, Ana").assertExists()
        val lista = compose.onNode(hasScrollAction())
        listOf("Sobra do mês", "Contas bancárias", "Cofrinhos por objetivo", "Metas do mês", "Por categoria", "Suas notas", "Girls Math", "Personalizar o Painel")
            .forEach { lista.performScrollToNode(hasText(it)) }
        compose.onNodeWithText("Personalizar o Painel").performClick()
        compose.onNodeWithText("Voltar ao padrão").assertExists()
    }
}
