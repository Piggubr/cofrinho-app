package br.com.piggu.feature.relatorios

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.Lancamentos
import br.com.piggu.core.data.RepositorioDoMes
import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.model.PigguRole
import br.com.piggu.core.model.Plano
import br.com.piggu.core.model.ResumoDoMes
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.core.testing.DIA_DAS_CAPTURAS
import br.com.piggu.core.testing.RedeDeTeste
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
import org.junit.Assert.assertArrayEquals
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
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap

/** O Room do guardado offline, na memória. */
private class GuardadoNaMemoria : RespostasGuardadasDao {
    val respostas = ConcurrentHashMap<String, RespostaGuardada>()
    override suspend fun ler(chave: String) = respostas[chave]
    override suspend fun guardar(resposta: RespostaGuardada) { respostas[resposta.chave] = resposta }
    override suspend fun apagarTudo() = respostas.clear()
}

/** A rede de teste com a Ana (Premium) logada, o cache na memória e um [Lancamentos]. */
private class Montagem(val rede: RedeDeTeste, val fixas: RespostasFixas, private val loja: ViewModelStore) {
    val guardado = GuardadoNaMemoria()
    val lancamentos = Lancamentos()
    val conectado = MutableStateFlow(true)
    private val cache = CacheDeRespostas(guardado, DIA_DAS_CAPTURAS)
    private var criados = 0

    fun relatorios(financas: ServicoDeFinancas = rede.financas): RelatoriosViewModel =
        ViewModelProvider.create(
            loja,
            viewModelFactory {
                initializer { RelatoriosViewModel(financas, RepositorioDoMes(cache, financas), cache, rede.sessao, lancamentos, DIA_DAS_CAPTURAS, conectado) }
            },
        )["vm${criados++}", RelatoriosViewModel::class]

    fun pedidos(inicio: String) = fixas.pedidos.filter { it.startsWith(inicio) }
}

private suspend fun TestScope.montar(pasta: TemporaryFolder, loja: ViewModelStore): Montagem {
    val fixas = RespostasFixas()
    return Montagem(redeDeTeste(pasta.root, fixas), fixas, loja).also { it.rede.entrar() }
}

/** A aba Relatórios (reports.ts e reports.spec.ts), com a API falsa da demonstração. */
@OptIn(ExperimentalCoroutinesApi::class)
class RelatoriosViewModelTest {
    @get:Rule val pasta = TemporaryFolder()
    private val loja = ViewModelStore()

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun devolver() {
        loja.clear()
    }

    @Test
    fun `o mes por categoria contra o anterior, com a projecao so no mes corrente`() = runTest {
        val montagem = montar(pasta, loja)
        val relatorios = montagem.relatorios()
        val estado = relatorios.estado.esperar { it.resumoPronto != null }
        val outubro = estado.resumoPronto!!
        val setembro = montagem.rede.financas.resumoDoMes("2026-09")
        assertEquals("2026-10", outubro.mes)
        assertTrue(outubro.porCategoria.isNotEmpty())
        outubro.porCategoria.forEach { c ->
            val antes = setembro.porCategoria.firstOrNull { it.categoria == c.categoria }?.total ?: BigDecimal.ZERO
            assertEquals(c.categoria, 0, antes.compareTo(c.anterior))
        }
        assertEquals(0, setembro.gastos.compareTo(outubro.gastosMesAnterior))
        // O ritmo de gasto até 28/10 levado aos 31 dias.
        val projecao = outubro.gastos.multiply(BigDecimal(31)).divide(BigDecimal(28), 2, RoundingMode.HALF_UP)
        assertEquals(0, projecao.compareTo(outubro.projecaoDeGastos))
        val maior = outubro.porCategoria.flatMap { listOf(it.total, it.anterior) }.max()
        assertEquals(0, maior.compareTo(estado.maiorDaCategoria))
        assertEquals(1f, estado.fracaoDoMes(maior))
        assertNull(estado.guardadoEm)

        relatorios.mudarMes("2026-09")
        val antes = relatorios.estado.esperar { it.resumoPronto?.mes == "2026-09" }
        assertNull(antes.resumoPronto!!.projecaoDeGastos)
        // Trocar de mês no mesmo ano não pede o ano de novo (trocarMes do site).
        assertEquals(1, montagem.pedidos("GET /api/reports/year").size)
    }

    @Test
    fun `o ano mes a mes para o Premium, com o grafico e a tabela`() = runTest {
        val montagem = montar(pasta, loja)
        val relatorios = montagem.relatorios()
        val estado = relatorios.estado.esperar { it.anoPronto != null }
        val ano = estado.anoPronto!!
        assertTrue(estado.ehPremium)
        assertEquals(2026, ano.ano)
        assertEquals((1..12).map { "2026-%02d".format(it) }, ano.meses.map { it.mes })
        assertEquals(0, ano.receitas.compareTo(ano.meses.fold(BigDecimal.ZERO) { s, m -> s + m.receitas }))
        assertEquals(0, ano.sobra.compareTo(ano.receitas - ano.gastos))
        val maior = ano.meses.flatMap { listOf(it.receitas, it.gastos) }.max()
        assertEquals(1f, estado.fracaoDoAno(maior))

        relatorios.estado.test {
            assertFalse(awaitItem().tabelaDoAno)
            relatorios.alternarTabelaDoAno()
            assertTrue(awaitItem().tabelaDoAno)
            relatorios.alternarTabelaDoMes()
            assertTrue(awaitItem().tabelaDoMes)
            relatorios.focarMes(9)
            assertEquals(9, awaitItem().mesEmFoco)
            relatorios.focarMes(9) // tocar de novo fecha a dica
            assertNull(awaitItem().mesEmFoco)
            relatorios.alternarTabelaDoAno()
            assertFalse(awaitItem().tabelaDoAno)
            cancelAndIgnoreRemainingEvents()
        }

        relatorios.focarMes(3)
        relatorios.mudarMes("2025-12")
        val passado = relatorios.estado.esperar { it.anoPronto?.ano == 2025 }
        assertNull(passado.mesEmFoco)
        assertEquals(2, montagem.pedidos("GET /api/reports/year").size)
    }

    @Test
    fun `sem Premium o ano vira convite e nem e pedido`() = runTest {
        val montagem = montar(pasta, loja)
        montagem.rede.sessao.entrou(montagem.rede.sessao.usuario!!.copy(plano = Plano.GRATUITO, role = PigguRole.TITULAR))
        val relatorios = montagem.relatorios()
        val estado = relatorios.estado.esperar { it.resumoPronto != null }
        assertFalse(estado.ehPremium)
        assertNull(estado.doAno)
        assertTrue(montagem.pedidos("GET /api/reports/year").isEmpty())
    }

    @Test
    fun `exporta o CSV do servidor com as colunas e o nome do site`() = runTest {
        val montagem = montar(pasta, loja)
        val relatorios = montagem.relatorios()
        relatorios.estado.esperar { it.resumoPronto != null }
        relatorios.exportar()
        val csv = relatorios.estado.esperar { it.csv != null }.csv!!
        assertEquals("piggu-gastos-2026-10.csv", csv.nome)
        assertArrayEquals(montagem.rede.financas.exportarGastos("2026-10").use { it.bytes() }, csv.bytes)
        val texto = csv.bytes.toString(Charsets.UTF_8)
        assertTrue(texto.startsWith("﻿data;item;categoria;valor;estabelecimento\n"))
        assertTrue(texto.lines().size > 1)
        relatorios.csvEntregue()
        assertNull(relatorios.estado.value.csv)
        assertFalse(relatorios.estado.value.exportando)

        // Sem internet o botão fica apagado e nada é pedido.
        montagem.conectado.value = false
        relatorios.estado.esperar { !it.conectado }
        val antes = montagem.pedidos("GET /api/expenses/export").size
        relatorios.exportar()
        assertFalse(relatorios.estado.value.podeExportar)
        assertEquals(antes, montagem.pedidos("GET /api/expenses/export").size)
    }

    @Test
    fun `erro ao exportar aparece na tela`() = runTest {
        val montagem = montar(pasta, loja)
        val falha = object : ServicoDeFinancas by montagem.rede.financas {
            override suspend fun exportarGastos(mes: String) = throw ErroDaApi.DoServidor(500, null, "Fora do ar.", emptyList(), null)
        }
        val relatorios = montagem.relatorios(falha)
        relatorios.exportar()
        val estado = relatorios.estado.esperar { it.erro != null }
        assertEquals(br.com.piggu.core.network.MensagemDeErro.DoServidor("Fora do ar."), estado.erro)
        assertNull(estado.csv)
        assertFalse(estado.exportando)
    }

    @Test
    fun `recarrega quando algo e lancado`() = runTest {
        val montagem = montar(pasta, loja)
        var chamadas = 0
        val contando = object : ServicoDeFinancas by montagem.rede.financas {
            override suspend fun resumoDoMes(mes: String, visao: String?): ResumoDoMes =
                montagem.rede.financas.resumoDoMes(mes, visao).copy(sobra = BigDecimal(++chamadas))
        }
        val relatorios = montagem.relatorios(contando)
        relatorios.estado.esperar { it.resumoPronto?.sobra == BigDecimal(1) }
        montagem.lancamentos.avisar()
        relatorios.estado.esperar { it.resumoPronto?.sobra == BigDecimal(2) }
        relatorios.estado.esperar { it.anoPronto != null }
        assertEquals(2, montagem.pedidos("GET /api/reports/year").size)
    }

    @Test
    fun `sem internet mostra o guardado com a hora, ou o erro quando nao ha nada`() = runTest {
        val montagem = montar(pasta, loja)
        montagem.relatorios().estado.esperar { it.resumoPronto != null && it.anoPronto != null }
        assertNotNull(montagem.guardado.respostas["resumo/2026-10"])

        val semRede = object : ServicoDeFinancas by montagem.rede.financas {
            override suspend fun resumoDoMes(mes: String, visao: String?): ResumoDoMes = throw ErroDaApi.SemInternet(IOException("sem rede"))
            override suspend fun relatorioDoAno(ano: Int) = throw ErroDaApi.SemInternet(IOException("sem rede"))
        }
        montagem.conectado.value = false
        val offline = montagem.relatorios(semRede)
        val guardado = offline.estado.esperar { it.guardadoEm != null && it.anoPronto != null }
        assertFalse(guardado.conectado)
        assertFalse(guardado.podeExportar)
        assertEquals("2026-10", guardado.resumoPronto!!.mes)

        offline.mudarMes("2026-07")
        val vazio = offline.estado.esperar { it.mes == "2026-07" && it.resumo is Carga.Falhou }
        assertTrue((vazio.resumo as Carga.Falhou).erro is ErroDaApi.SemInternet)
        assertNull(vazio.guardadoEm)
    }
}
