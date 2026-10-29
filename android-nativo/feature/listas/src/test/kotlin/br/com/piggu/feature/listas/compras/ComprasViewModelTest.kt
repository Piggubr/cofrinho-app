package br.com.piggu.feature.listas.compras

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.model.ItemDeCompra
import br.com.piggu.core.model.ListaDeCompras
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
import kotlinx.coroutines.CompletableDeferred
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

private val EstadoDasCompras.titulosPendentes get() = pendentes.map { it.item }
private val EstadoDasCompras.titulosComprados get() = comprados.map { it.item }

/**
 * Compras (shopping.ts e shopping.spec.ts), com a API falsa da demonstração: em Compras, leite e
 * café a comprar e o detergente já no carrinho; em Desejos, air fryer e Kindle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComprasViewModelTest {
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

    @After fun devolver() {
        loja.clear()
    }

    private suspend fun TestScope.entrar() {
        fixas = RespostasFixas()
        rede = redeDeTeste(pasta.root, fixas)
        rede.entrar()
    }

    private fun compras(estiloDeVida: ServicoDeEstiloDeVida = rede.estiloDeVida): ComprasViewModel =
        ViewModelProvider.create(
            loja,
            viewModelFactory { initializer { ComprasViewModel(estiloDeVida, cache, DIA_DAS_CAPTURAS, conectado) } },
        )["vm${criados++}", ComprasViewModel::class]

    private fun pedidos(inicio: String) = fixas.pedidos.filter { it.startsWith(inicio) }

    @Test
    fun `separa pendentes e comprados, e cada lista mostra so os seus`() = runTest {
        entrar()
        val compras = compras()
        val estado = compras.estado.esperar { it.carga is Carga.Pronto }
        assertEquals(listOf("Leite integral", "Café 500g"), estado.titulosPendentes)
        assertEquals(listOf("Detergente"), estado.titulosComprados)

        compras.trocarLista(ListaDeCompras.DESEJOS)
        assertEquals(listOf("Air fryer", "Kindle"), compras.estado.value.titulosPendentes)
        assertTrue(compras.estado.value.comprados.isEmpty())
    }

    @Test
    fun `adiciona na lista aberta e limpa os campos`() = runTest {
        entrar()
        val compras = compras()
        compras.estado.esperar { it.carga is Carga.Pronto }
        compras.trocarLista(ListaDeCompras.DESEJOS)
        compras.mudarItem(" Tênis ")
        compras.mudarQuantidade("1 par")
        compras.adicionar()

        val depois = compras.estado.esperar { it.item.isEmpty() }
        assertEquals(listOf("""POST /api/shopping/items {"item":"Tênis","quantidade":"1 par","lista":"Desejos"}"""), pedidos("POST /api/shopping"))
        assertEquals("Tênis", depois.titulosPendentes.first())
        assertEquals("", depois.quantidade)
        assertNull(depois.erro)
    }

    @Test
    fun `item vazio ou busca curta nao chamam o backend`() = runTest {
        entrar()
        val compras = compras()
        compras.estado.esperar { it.carga is Carga.Pronto }

        compras.mudarItem("   ")
        compras.adicionar()
        assertEquals(MensagemDeErro.DoApp(R.string.compras_falta_item), compras.estado.value.erro)

        compras.mudarBusca("a")
        compras.buscar()
        assertEquals(MensagemDeErro.DoApp(R.string.compras_busca_curta), compras.estado.value.erro)
        assertTrue(pedidos("POST /api/shopping").isEmpty())
        assertTrue(pedidos("GET /api/shopping/catalog").isEmpty())
    }

    @Test
    fun `busca no catalogo e adiciona com marca e codigo`() = runTest {
        entrar()
        val compras = compras()
        compras.estado.esperar { it.carga is Carga.Pronto }
        compras.mudarBusca("leite")
        compras.buscar()
        val achou = compras.estado.esperar { it.resultados.isNotEmpty() && !it.buscando }
        val produto = achou.resultados.single()
        assertEquals("Leite condensado", produto.nome)

        compras.adicionarDoCatalogo(produto)
        val depois = compras.estado.esperar { it.resultados.isEmpty() }
        assertEquals(
            listOf(
                """POST /api/shopping/items {"item":"Leite condensado","quantidade":"395 g","lista":"Compras",""" +
                    """"marca":"Moça","imagem":"","codigo":"7891000100103"}""",
            ),
            pedidos("POST /api/shopping"),
        )
        assertEquals("", depois.busca)
        assertEquals("Leite condensado", depois.pendentes.first().item)
        assertEquals("Moça", depois.pendentes.first().marca)
    }

    @Test
    fun `o codigo lido busca no catalogo por ele, e o leitor que nao abre avisa`() = runTest {
        entrar()
        val compras = compras()
        compras.estado.esperar { it.carga is Carga.Pronto }

        compras.codigoLido("7896004000015")
        val achou = compras.estado.esperar { it.resultados.isNotEmpty() }
        assertEquals("7896004000015", achou.busca)
        assertEquals("Café torrado e moído", achou.resultados.single().nome)

        compras.leitorIndisponivel()
        assertEquals(MensagemDeErro.DoApp(R.string.compras_leitor_indisponivel), compras.estado.value.erro)
    }

    @Test
    fun `riscar vale na hora e, se o servidor recusar, volta e avisa`() = runTest {
        entrar()
        val resposta = CompletableDeferred<Unit>()
        val recusa = object : ServicoDeEstiloDeVida by rede.estiloDeVida {
            override suspend fun marcarComprado(id: String, comprado: Boolean): ItemDeCompra {
                resposta.await()
                throw ErroDaApi.DoServidor(500, null, "Sem conexão com a lista.", emptyList(), null)
            }
        }
        val compras = compras(recusa)
        val leite = compras.estado.esperar { it.carga is Carga.Pronto }.pendentes.first { it.item == "Leite integral" }

        compras.alternarComprado(leite)
        assertEquals(listOf("Detergente", "Leite integral").sorted(), compras.estado.value.titulosComprados.sorted())

        resposta.complete(Unit)
        val desfeito = compras.estado.esperar { it.erro != null }
        assertEquals(listOf("Detergente"), desfeito.titulosComprados)
        assertEquals(MensagemDeErro.DoServidor("Sem conexão com a lista."), desfeito.erro)
    }

    @Test
    fun `riscar e desriscar chegam ao servidor`() = runTest {
        entrar()
        val respondeu = CompletableDeferred<ItemDeCompra>()
        val avisando = object : ServicoDeEstiloDeVida by rede.estiloDeVida {
            override suspend fun marcarComprado(id: String, comprado: Boolean) =
                rede.estiloDeVida.marcarComprado(id, comprado).also { respondeu.complete(it) }
        }
        val compras = compras(avisando)
        val detergente = compras.estado.esperar { it.carga is Carga.Pronto }.comprados.single()

        compras.alternarComprado(detergente)
        assertTrue("Detergente" in compras.estado.value.titulosPendentes)
        assertFalse(respondeu.await().comprado)
        assertEquals(listOf("PATCH /api/shopping/items/${detergente.id}/purchased"), pedidos("PATCH"))
        assertFalse(rede.estiloDeVida.listarCompras().first { it.id == detergente.id }.comprado)
    }

    @Test
    fun `apagar tira o item da lista`() = runTest {
        entrar()
        val compras = compras()
        val leite = compras.estado.esperar { it.carga is Carga.Pronto }.pendentes.first { it.item == "Leite integral" }

        compras.excluir(leite)
        compras.estado.esperar { "Leite integral" !in it.titulosPendentes }
        assertEquals(listOf("DELETE /api/shopping/items/${leite.id}"), pedidos("DELETE"))
    }

    @Test
    fun `sem internet mostra o guardado com a hora, ou o erro quando nao ha nada`() = runTest {
        entrar()
        compras().estado.esperar { it.carga is Carga.Pronto }
        assertNotNull(guardado.respostas["compras"])

        val semRede = object : ServicoDeEstiloDeVida by rede.estiloDeVida {
            override suspend fun listarCompras(lista: String?): List<ItemDeCompra> = throw ErroDaApi.SemInternet(IOException("sem rede"))
        }
        conectado.value = false
        val comGuardado = compras(semRede).estado.esperar { it.guardadoEm != null }
        assertEquals(listOf("Leite integral", "Café 500g"), comGuardado.titulosPendentes)
        assertFalse(comGuardado.conectado)

        guardado.respostas.remove("compras")
        val semNada = compras(semRede).estado.esperar { it.carga is Carga.Falhou }
        assertEquals(FalhaAoCarregar.SemInternet, falhaDe((semNada.carga as Carga.Falhou).erro))
        assertNull(semNada.guardadoEm)
    }

    @Test
    fun `erro do servidor pede tentar de novo`() = runTest {
        entrar()
        var falhar = true
        val instavel = object : ServicoDeEstiloDeVida by rede.estiloDeVida {
            override suspend fun listarCompras(lista: String?): List<ItemDeCompra> =
                if (falhar) throw ErroDaApi.DoServidor(503, null, "Fora do ar.", emptyList(), null) else rede.estiloDeVida.listarCompras(lista)
        }
        val compras = compras(instavel)
        val falhou = compras.estado.esperar { it.carga is Carga.Falhou }
        assertEquals(FalhaAoCarregar.DoServidor, falhaDe((falhou.carga as Carga.Falhou).erro))
        falhar = false
        compras.tentarDeNovo()
        assertEquals(2, compras.estado.esperar { it.carga is Carga.Pronto }.pendentes.size)
    }
}
