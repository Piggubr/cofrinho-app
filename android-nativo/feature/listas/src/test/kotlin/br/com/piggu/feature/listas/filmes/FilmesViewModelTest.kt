package br.com.piggu.feature.listas.filmes

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.ListasParaLancar
import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.model.Filme
import br.com.piggu.core.model.FilmeDoCatalogo
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeEstiloDeVida
import br.com.piggu.core.testing.DIA_DAS_CAPTURAS
import br.com.piggu.core.testing.RedeDeTeste
import br.com.piggu.core.testing.RespostasFixas
import br.com.piggu.core.testing.esperar
import br.com.piggu.core.testing.redeDeTeste
import br.com.piggu.core.ui.FalhaAoCarregar
import br.com.piggu.feature.listas.R
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
import java.util.concurrent.CopyOnWriteArrayList

private class GuardadoNaMemoria : RespostasGuardadasDao {
    val respostas = ConcurrentHashMap<String, RespostaGuardada>()
    override suspend fun ler(chave: String) = respostas[chave]
    override suspend fun guardar(resposta: RespostaGuardada) { respostas[resposta.chave] = resposta }
    override suspend fun apagarTudo() = respostas.clear()
}

/**
 * Filmes (movies.ts e movies.spec.ts), com a API falsa da demonstração: Divertida Mente 2 (Ana 5,
 * Beto 4) e Duna: Parte Dois (Beto 5) já assistidos, e Ainda Estou Aqui na fila.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FilmesViewModelTest {
    @get:Rule val pasta = TemporaryFolder()
    private val loja = ViewModelStore()
    private val guardado = GuardadoNaMemoria()
    private val conectado = MutableStateFlow(true)
    private val cache = CacheDeRespostas(guardado, DIA_DAS_CAPTURAS)
    private lateinit var fixas: RespostasFixas
    private lateinit var rede: RedeDeTeste
    private var criados = 0

    /** O gênero de cada sorteio (as RespostasFixas guardam o caminho sem a consulta). */
    private val generos = CopyOnWriteArrayList<String>()

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())
    // Sem resetMain: resposta atrasada da API falsa (outra thread) iria para um Main desfeito e derrubava o teste seguinte no CI.

    @After fun devolver() {
        loja.clear()
    }

    private suspend fun TestScope.entrar() {
        fixas = RespostasFixas()
        rede = redeDeTeste(pasta.root, fixas)
        rede.entrar()
    }

    private fun contando(servico: ServicoDeEstiloDeVida) = object : ServicoDeEstiloDeVida by servico {
        override suspend fun sortearFilme(genero: String?) = servico.sortearFilme(genero).also { generos += genero.toString() }
    }

    private fun filmes(servico: ServicoDeEstiloDeVida = rede.estiloDeVida): FilmesViewModel =
        ViewModelProvider.create(
            loja,
            viewModelFactory {
                initializer {
                    FilmesViewModel(
                        contando(servico), cache, ListasParaLancar(cache, rede.financas, rede.identidade), rede.sessao,
                        DIA_DAS_CAPTURAS, conectado,
                    )
                }
            },
        )["vm${criados++}", FilmesViewModel::class]

    private fun pedidos(inicio: String) = fixas.pedidos.filter { it.startsWith(inicio) }

    private fun EstadoDosFilmes.filme(titulo: String): Filme = filmes.first { it.titulo == titulo }

    @Test
    fun `separa a fila dos assistidos e mostra a nota de cada pessoa`() = runTest {
        entrar()
        val estado = filmes().estado.esperar { it.carga is Carga.Pronto && it.membros.isNotEmpty() }
        assertEquals(listOf("Ainda Estou Aqui"), estado.paraVer.map { it.titulo })
        assertEquals(listOf("Divertida Mente 2", "Duna: Parte Dois"), estado.assistidos.map { it.titulo })
        val divertida = estado.filme("Divertida Mente 2")
        assertEquals(5, estado.minhaNota(divertida))
        assertEquals(listOf(NotaDeOutro("Beto Souza", 4)), estado.outrasNotas(divertida))
        val duna = estado.filme("Duna: Parte Dois")
        assertEquals(0, estado.minhaNota(duna))
        assertEquals(listOf(NotaDeOutro("Beto Souza", 5)), estado.outrasNotas(duna))
        assertEquals(listOf(NotaDeOutro(null, 3)), estado.outrasNotas(duna.copy(avaliacoes = mapOf("u-antigo" to 3))))
        assertNull(estado.guardadoEm)
    }

    @Test
    fun `busca so com duas letras e adiciona o achado, limpando a busca`() = runTest {
        entrar()
        val filmes = filmes()
        filmes.estado.esperar { it.carga is Carga.Pronto }
        filmes.mudarBusca(" c ")
        filmes.buscar()
        assertEquals(MensagemDeErro.DoApp(R.string.filmes_busca_curta), filmes.estado.value.erro)
        assertTrue(pedidos("GET /api/movies/search").isEmpty())

        filmes.mudarBusca(" cidade ")
        filmes.buscar()
        val achou = filmes.estado.esperar { it.resultados.isNotEmpty() && !it.buscando }
        assertNull(achou.erro)
        val cidade = achou.resultados.single()
        assertEquals("Cidade de Deus", cidade.titulo)

        filmes.adicionar(cidade)
        val depois = filmes.estado.esperar { e -> e.filmes.firstOrNull()?.titulo == "Cidade de Deus" }
        assertTrue(pedidos("POST /api/movies").single().startsWith("""POST /api/movies {"tmdbId":"demo-2","titulo":"Cidade de Deus""""))
        assertTrue(depois.resultados.isEmpty())
        assertEquals("", depois.busca)
        assertEquals(listOf("Cidade de Deus", "Ainda Estou Aqui"), depois.paraVer.map { it.titulo })
    }

    @Test
    fun `sorteia pelo genero escolhido, ou por qualquer um, e adiciona o sorteado`() = runTest {
        entrar()
        val filmes = filmes()
        filmes.estado.esperar { it.carga is Carga.Pronto }
        filmes.sortear()
        filmes.estado.esperar { it.sorteado != null && !it.sorteando }
        filmes.mudarGenero("35")
        filmes.sortear()
        filmes.estado.esperar { generos.size == 2 && !it.sorteando }
        assertEquals(listOf("null", "35"), generos)

        val sorteado = filmes.estado.value.sorteado!!
        filmes.adicionar(sorteado)
        val depois = filmes.estado.esperar { e -> e.filmes.any { it.tmdbId == sorteado.tmdbId } }
        assertNull(depois.sorteado)
    }

    @Test
    fun `filme repetido mostra o erro do servidor`() = runTest {
        entrar()
        val filmes = filmes()
        filmes.estado.esperar { it.carga is Carga.Pronto }
        val toy = FilmeDoCatalogo("demo-4", "Toy Story", "1995")
        filmes.adicionar(toy)
        filmes.estado.esperar { e -> e.filmes.any { it.tmdbId == "demo-4" } }
        filmes.adicionar(toy)
        val recusado = filmes.estado.esperar { it.erro != null }
        assertEquals(MensagemDeErro.DoServidor("Esse filme já está na lista."), recusado.erro)
    }

    @Test
    fun `marca como assistido, avalia e volta para a fila`() = runTest {
        entrar()
        val filmes = filmes()
        val ainda = filmes.estado.esperar { it.carga is Carga.Pronto }.filme("Ainda Estou Aqui")
        filmes.alternarAssistido(ainda)
        filmes.estado.esperar { it.filme("Ainda Estou Aqui").assistido }
        assertEquals(listOf("PATCH /api/movies/${ainda.id}/watched"), pedidos("PATCH"))

        filmes.avaliar(filmes.estado.value.filme("Ainda Estou Aqui"), 4)
        val avaliado = filmes.estado.esperar { e -> e.minhaNota(e.filme("Ainda Estou Aqui")) == 4 }
        assertEquals(listOf("""PUT /api/movies/${ainda.id}/rating {"nota":4}"""), pedidos("PUT"))
        assertTrue(avaliado.paraVer.isEmpty())

        filmes.alternarAssistido(avaliado.filme("Ainda Estou Aqui"))
        filmes.estado.esperar { !it.filme("Ainda Estou Aqui").assistido }
        assertEquals(2, pedidos("PATCH").size)
    }

    @Test
    fun `tira da lista so depois de confirmar`() = runTest {
        entrar()
        val filmes = filmes()
        val duna = filmes.estado.esperar { it.carga is Carga.Pronto }.filme("Duna: Parte Dois")
        filmes.pedirParaTirar(duna)
        assertEquals(duna, filmes.estado.value.tirando)
        filmes.desistir()
        assertTrue(pedidos("DELETE").isEmpty())

        filmes.pedirParaTirar(duna)
        filmes.confirmar()
        val depois = filmes.estado.esperar { e -> e.filmes.none { it.id == duna.id } }
        assertNull(depois.tirando)
        assertEquals(listOf("DELETE /api/movies/${duna.id}"), pedidos("DELETE"))
    }

    @Test
    fun `sem internet mostra o guardado com a hora, ou o erro quando nao ha nada`() = runTest {
        entrar()
        val semRede = object : ServicoDeEstiloDeVida by rede.estiloDeVida {
            override suspend fun listarFilmes(): List<Filme> = throw ErroDaApi.SemInternet(IOException("sem rede"))
        }
        conectado.value = false
        val vazio = filmes(semRede).estado.esperar { it.carga is Carga.Falhou }
        assertEquals(FalhaAoCarregar.SemInternet, falhaDe((vazio.carga as Carga.Falhou).erro))

        conectado.value = true
        filmes().estado.esperar { it.carga is Carga.Pronto }
        assertNotNull(guardado.respostas["filmes"])
        conectado.value = false
        val offline = filmes(semRede).estado.esperar { it.guardadoEm != null }
        assertEquals(3, offline.filmes.size)
        assertFalse(offline.conectado)
    }
}
