package br.com.piggu.feature.assistente.busca

import br.com.piggu.core.model.ModuloDeEstiloDeVida
import br.com.piggu.core.model.Preferencias
import br.com.piggu.core.testing.RedeDeTeste
import br.com.piggu.core.testing.RespostasFixas
import br.com.piggu.core.testing.esperar
import br.com.piggu.core.testing.redeDeTeste
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** busca-global.spec.ts, com a sessão de verdade respondida pela API falsa da demonstração. */
@OptIn(ExperimentalCoroutinesApi::class)
class BuscaViewModelTest {
    @get:Rule val pasta = TemporaryFolder()

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())
    // Sem resetMain: resposta atrasada da API falsa (outra thread) iria para um Main desfeito e derrubava o teste seguinte no CI.
    private val fixas = RespostasFixas()

    private suspend fun TestScope.entrar(): RedeDeTeste = redeDeTeste(pasta.root, fixas).also { it.entrar() }

    private fun RedeDeTeste.busca() = BuscaViewModel(sessao, financas, estiloDeVida)

    private fun buscasDeGastos() = fixas.pedidos.count { it.startsWith("GET /api/expenses/search") }

    @Test
    fun `telas desde a primeira letra e gastos so com 2 letras, depois da espera`() = runTest {
        val busca = entrar().busca()
        busca.mudarTelas(listOf("Filmes" to "/filmes", "Gastos" to "/gastos"))
        busca.abrir()

        busca.digitar("f")
        val telas = busca.estado.esperar { it.termo == "f" }.resultados
        assertEquals(listOf("Filmes"), telas.map { it.titulo })
        assertEquals(DestinoDaBusca.Caminho("/filmes"), telas.single().destino)
        advanceTimeBy(ESPERA_DA_BUSCA_MS + 50)
        runCurrent()
        assertEquals(0, buscasDeGastos())

        // Digitando rápido, só a última vai ao servidor.
        busca.digitar("ja")
        busca.digitar("jan")
        busca.digitar("jantar")
        advanceTimeBy(ESPERA_DA_BUSCA_MS - 1)
        runCurrent()
        assertEquals(0, buscasDeGastos())
        advanceTimeBy(2)
        runCurrent()
        val achou = busca.estado.esperar { estado -> estado.resultados.any { it.grupo == GrupoDaBusca.Gastos } }
        assertTrue(achou.resultados.all { it.grupo == GrupoDaBusca.Gastos && it.titulo == "Jantar de sexta" })
        assertEquals(1, buscasDeGastos())
    }

    @Test
    fun `acha gastos de outros meses e leva ao mes de cada um`() = runTest {
        val busca = entrar().busca()
        busca.abrir()
        busca.digitar("jantar")
        advanceTimeBy(ESPERA_DA_BUSCA_MS + 1)
        runCurrent()
        val gastos = busca.estado.esperar { e -> e.resultados.any { it.grupo == GrupoDaBusca.Gastos } }.resultados
        val meses = gastos.map { (it.destino as DestinoDaBusca.Gasto).mes }.toSet()
        assertTrue("meses: $meses", meses.size >= 2)
        assertTrue(meses.all { Regex("""\d{4}-\d{2}""").matches(it) })
        // "28/09/2026 · R$ 143,52": a data e o valor na moeda da pessoa.
        assertTrue(gastos.first().detalhe, Regex("""\d{2}/\d{2}/\d{4} · R\$.+""").matches(gastos.first().detalhe))
    }

    @Test
    fun `nada encontrado so depois do minimo de letras`() = runTest {
        val busca = entrar().busca()
        busca.abrir()
        busca.digitar("z")
        assertFalse(busca.estado.esperar { it.termo == "z" }.nadaEncontrado)
        busca.digitar("zzz")
        assertTrue(busca.estado.esperar { it.termo == "zzz" }.nadaEncontrado)
    }

    @Test
    fun `lugares, filmes e compras filtrados no aparelho, sem acento`() = runTest {
        val busca = entrar().busca()
        busca.abrir()
        busca.digitar("cafe")
        val estado = busca.estado.esperar { e -> e.resultados.any { it.grupo == GrupoDaBusca.Compras } }
        val titulos = estado.resultados.associate { it.titulo to it.grupo }
        assertEquals(GrupoDaBusca.Lugares, titulos["Café do Mirante"])
        assertEquals(GrupoDaBusca.Compras, titulos["Café 500g"])
        assertEquals(DestinoDaBusca.Caminho("/compras"), estado.resultados.first { it.titulo == "Café 500g" }.destino)
    }

    @Test
    fun `modulo desligado nem e buscado nem aparece`() = runTest {
        val rede = entrar()
        val ana = rede.sessao.usuario!!
        rede.sessao.entrou(ana.copy(preferencias = (ana.preferencias ?: Preferencias()).copy(modulos = listOf(ModuloDeEstiloDeVida.FILMES))))
        val busca = rede.busca()
        busca.abrir()
        busca.digitar("duna")
        busca.estado.esperar { e -> e.resultados.any { it.grupo == GrupoDaBusca.Filmes } }
        busca.digitar("cafe")
        assertTrue(busca.estado.esperar { it.termo == "cafe" }.resultados.none { it.grupo == GrupoDaBusca.Lugares || it.grupo == GrupoDaBusca.Compras })
        assertTrue(fixas.pedidos.none { it.startsWith("GET /api/places") || it.startsWith("GET /api/shopping") })
    }
}
