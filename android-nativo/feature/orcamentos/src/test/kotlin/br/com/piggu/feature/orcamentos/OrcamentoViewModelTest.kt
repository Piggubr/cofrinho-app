package br.com.piggu.feature.orcamentos

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.Lancamentos
import br.com.piggu.core.data.ListasParaLancar
import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.model.AlertaDoOrcamento
import br.com.piggu.core.model.Gasto
import br.com.piggu.core.model.Plano
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.core.testing.DIA_DAS_CAPTURAS
import br.com.piggu.core.testing.RedeDeTeste
import br.com.piggu.core.testing.RespostaFixa
import br.com.piggu.core.testing.RespostasFixas
import br.com.piggu.core.testing.esperar
import br.com.piggu.core.testing.redeDeTeste
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
import org.robolectric.annotation.Config
import java.io.IOException
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

private class GuardadoNaMemoria : RespostasGuardadasDao {
    val respostas = ConcurrentHashMap<String, RespostaGuardada>()
    override suspend fun ler(chave: String) = respostas[chave]
    override suspend fun guardar(resposta: RespostaGuardada) { respostas[resposta.chave] = resposta }
    override suspend fun apagarTudo() = respostas.clear()
}

private val EstadoDoOrcamento.pronto: Boolean get() = dados is Carga.Pronto

/**
 * O Orçamento (budgets.spec.ts e goals.spec.ts), com a API falsa da demonstração (Ana, Premium).
 * No Robolectric: uma resposta atrasada que chega no fim do teste encontra o Main do Android.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class OrcamentoViewModelTest {
    @get:Rule val pasta = TemporaryFolder()
    private val loja = ViewModelStore()
    private lateinit var rede: RedeDeTeste
    private lateinit var fixas: RespostasFixas
    private val guardado = GuardadoNaMemoria()
    private val lancamentos = Lancamentos()
    private val conectado = MutableStateFlow(true)
    private var criados = 0

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun devolver() {
        loja.clear()
    }

    private suspend fun TestScope.entrar(fixas: RespostasFixas = RespostasFixas()) {
        this@OrcamentoViewModelTest.fixas = fixas
        rede = redeDeTeste(pasta.root, fixas).also { it.entrar() }
    }

    private fun orcamento(financas: ServicoDeFinancas = rede.financas): OrcamentoViewModel {
        val cache = CacheDeRespostas(guardado, DIA_DAS_CAPTURAS)
        val factory = viewModelFactory {
            initializer { OrcamentoViewModel(financas, cache, ListasParaLancar(cache, financas, rede.identidade), rede.sessao, lancamentos, DIA_DAS_CAPTURAS, conectado) }
        }
        return ViewModelProvider.create(loja, factory)["vm${criados++}", OrcamentoViewModel::class]
    }

    private fun pedidos(inicio: String) = fixas.pedidos.filter { it.startsWith(inicio) }

    @Test
    fun `limite do mes mostra o consumido, define outro mes e lista todos`() = runTest {
        entrar()
        val vm = orcamento()
        val estado = vm.estado.esperar { it.pronto }
        assertEquals("2026-10", estado.mesAtual)
        assertTrue(estado.premium)
        assertEquals(0, estado.metaDoMesAtual.compareTo(BigDecimal(4500)))
        val gastos = rede.financas.listarGastos("2026-10").fold(BigDecimal.ZERO) { s, g -> s + g.valor }
        assertEquals(0, estado.gastoDoMes.compareTo(gastos))
        assertEquals(gastos.multiply(BigDecimal(100)).divide(BigDecimal(4500), 0, java.math.RoundingMode.HALF_UP).toInt().coerceAtMost(100), estado.progresso)
        assertEquals(listOf("2026-10", "2026-09", "2026-08"), estado.outrosMeses.map { it.first })
        // O formulário já vem com o limite do mês atual, como no site.
        assertEquals(0, estado.limite!!.compareTo(BigDecimal(4500)))

        vm.mudarMesDoLimite("2026-12")
        assertNull(vm.estado.value.limite)
        vm.mudarLimite(BigDecimal("5000"))
        vm.salvarLimite()
        val depois = vm.estado.esperar { it.outrosMeses.firstOrNull()?.first == "2026-12" && !it.salvando }
        assertEquals(listOf("""PUT /api/monthly-goals {"mes":"2026-12","limite":5000}"""), pedidos("PUT /api/monthly-goals"))
        assertNull(depois.erro)
        assertEquals(1, lancamentos.versao.value) // o Painel também fica sabendo

        vm.editarMeta("2026-09")
        assertEquals("2026-09", vm.estado.value.mesDoLimite)
        assertEquals(0, vm.estado.value.limite!!.compareTo(BigDecimal(4500)))
    }

    @Test
    fun `limite estourado para a barra em 100 e limite vazio ou zero nao vai ao backend`() = runTest {
        entrar(RespostasFixas("GET /api/monthly-goals" to RespostaFixa(200, """{"2026-10":1}""")))
        val vm = orcamento()
        val estado = vm.estado.esperar { it.pronto }
        assertTrue(estado.estourou)
        assertEquals(100, estado.progresso)

        vm.mudarLimite(BigDecimal.ZERO)
        vm.salvarLimite()
        assertEquals(MensagemDeErro.DoApp(R.string.orcamento_limite_invalido), vm.estado.value.erro)
        vm.mudarLimite(null)
        vm.salvarLimite()
        assertTrue(pedidos("PUT").isEmpty())
    }

    @Test
    fun `sem limite no mes o estado e vazio`() = runTest {
        entrar(
            RespostasFixas(
                "GET /api/monthly-goals" to RespostaFixa(200, "{}"),
                "GET /api/budgets" to RespostaFixa(200, "[]"),
            ),
        )
        val estado = orcamento().estado.esperar { it.pronto }
        assertEquals(0, estado.metaDoMesAtual.signum())
        assertEquals(0, estado.progresso)
        assertFalse(estado.estourou)
        assertTrue(estado.outrosMeses.isEmpty())
        assertTrue(estado.orcamentos.isEmpty())
        assertNull(estado.limite)
    }

    @Test
    fun `por categoria mostra os alertas de 80 e 100 e muda o limite`() = runTest {
        entrar()
        val vm = orcamento()
        val inicio = vm.estado.esperar { it.pronto && it.categorias.isNotEmpty() }
        val alvo = inicio.orcamentos.first { it.gasto.signum() > 0 }

        // No limite exato: 100%, estourou.
        vm.editarOrcamento(alvo)
        assertEquals(alvo.categoria, vm.estado.value.categoria)
        vm.mudarLimiteDaCategoria(alvo.gasto)
        vm.salvarCategoria()
        val cheio = vm.estado.esperar { e -> e.orcamentos.any { it.id == alvo.id && it.limite.compareTo(alvo.gasto) == 0 } }
        assertEquals(AlertaDoOrcamento.ESTOUROU, cheio.orcamentos.first { it.id == alvo.id }.alerta)
        assertEquals("", cheio.categoria)
        assertNull(cheio.limiteDaCategoria)

        // Gasto em 85% do limite: atenção.
        val limite = alvo.gasto.multiply(BigDecimal(100)).divide(BigDecimal(85), 2, java.math.RoundingMode.HALF_UP)
        vm.mudarCategoria(alvo.categoria)
        vm.mudarLimiteDaCategoria(limite)
        vm.salvarCategoria()
        val atencao = vm.estado.esperar { e -> e.orcamentos.any { it.id == alvo.id && it.limite.compareTo(limite) == 0 } }
        assertEquals(AlertaDoOrcamento.ATENCAO, atencao.orcamentos.first { it.id == alvo.id }.alerta)
        assertEquals(2, pedidos("PUT /api/budgets").size)
    }

    @Test
    fun `por categoria sem categoria ou sem limite nada vai ao backend, e o erro do servidor aparece`() = runTest {
        entrar(RespostasFixas("PUT /api/budgets" to RespostasFixas.erro(402, "PREMIUM", "Recurso do Piggu Premium.")))
        val vm = orcamento()
        vm.estado.esperar { it.pronto }
        vm.mudarLimiteDaCategoria(BigDecimal("100"))
        vm.salvarCategoria()
        assertEquals(MensagemDeErro.DoApp(R.string.orcamento_categoria_invalida), vm.estado.value.erro)
        assertTrue(pedidos("PUT").isEmpty())

        vm.estado.test {
            awaitItem()
            vm.mudarCategoria("Lazer")
            awaitItem()
            vm.salvarCategoria()
            assertTrue(awaitItem().salvando)
            val falhou = awaitItem()
            assertFalse(falhou.salvando)
            assertEquals(MensagemDeErro.DoServidor("Recurso do Piggu Premium."), falhou.erro)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, lancamentos.versao.value)
    }

    @Test
    fun `sem Premium ve e apaga, mas criar fica no convite`() = runTest {
        entrar()
        rede.sessao.entrou(rede.sessao.usuario!!.copy(plano = Plano.GRATUITO))
        val vm = orcamento()
        val estado = vm.estado.esperar { it.pronto && !it.premium }
        assertEquals(3, estado.orcamentos.size)

        vm.mudarCategoria("Lazer")
        vm.mudarLimiteDaCategoria(BigDecimal("100"))
        vm.salvarCategoria()
        assertTrue(pedidos("PUT").isEmpty())

        val lazer = estado.orcamentos.first { it.categoria == "Lazer" }
        vm.excluir(lazer)
        val depois = vm.estado.esperar { e -> e.orcamentos.none { it.id == lazer.id } }
        assertEquals(2, depois.orcamentos.size)
        assertEquals(listOf("DELETE /api/budgets/${lazer.id}"), pedidos("DELETE"))
    }

    @Test
    fun `excluir com erro mostra a mensagem e nada muda`() = runTest {
        entrar(RespostasFixas("DELETE /api/budgets/orc-lazer" to RespostasFixas.erro(404, "NAO_ENCONTRADO", "Orçamento não encontrado.")))
        val vm = orcamento()
        val estado = vm.estado.esperar { it.pronto }
        vm.excluir(estado.orcamentos.first { it.id == "orc-lazer" })
        val falhou = vm.estado.esperar { it.erro != null }
        assertEquals(MensagemDeErro.DoServidor("Orçamento não encontrado."), falhou.erro)
        assertEquals(3, falhou.orcamentos.size)
        assertEquals(0, lancamentos.versao.value)
    }

    @Test
    fun `recarrega quando algo e lancado`() = runTest {
        entrar()
        val vm = orcamento()
        vm.estado.esperar { it.pronto }
        val antes = pedidos("GET /api/budgets").size
        lancamentos.avisar()
        vm.estado.esperar { pedidos("GET /api/budgets").size > antes && it.pronto }
    }

    @Test
    fun `sem internet mostra o guardado com a hora, ou o erro quando nao ha nada`() = runTest {
        entrar()
        orcamento().estado.esperar { it.pronto }
        assertNotNull(guardado.respostas["orcamento/2026-10"])

        val semRede = object : ServicoDeFinancas by rede.financas {
            override suspend fun listarGastos(mes: String?, visao: String?): List<Gasto> = throw ErroDaApi.SemInternet(IOException("sem rede"))
        }
        conectado.value = false
        val offline = orcamento(semRede).estado.esperar { it.guardadoEm != null }
        assertFalse(offline.conectado)
        assertEquals(3, offline.orcamentos.size)

        guardado.respostas.clear()
        val vazio = orcamento(semRede).estado.esperar { it.dados is Carga.Falhou }
        assertTrue((vazio.dados as Carga.Falhou).erro is ErroDaApi.SemInternet)
        assertNull(vazio.guardadoEm)
    }
}
