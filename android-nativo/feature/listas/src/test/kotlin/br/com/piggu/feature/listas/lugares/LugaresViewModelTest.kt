package br.com.piggu.feature.listas.lugares

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.model.Lugar
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
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

private class GuardadoNaMemoria : RespostasGuardadasDao {
    val respostas = ConcurrentHashMap<String, RespostaGuardada>()
    override suspend fun ler(chave: String) = respostas[chave]
    override suspend fun guardar(resposta: RespostaGuardada) { respostas[resposta.chave] = resposta }
    override suspend fun apagarTudo() = respostas.clear()
}

private val EstadoDosLugares.lugares: List<Lugar>? get() = dados?.lugares

/** Uma foto de câmera grande, em JPEG. */
private fun fotoGrande(largura: Int, altura: Int): ByteArray =
    ByteArrayOutputStream().also { Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        .toByteArray()

/**
 * Lugares (places.ts e places.spec.ts), com a API falsa da demonstração: a Cantina da Nonna
 * (setembro), o Café do Mirante e o Parque Ibirapuera (agosto), e quatro marcadores.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LugaresViewModelTest {
    @get:Rule val pasta = TemporaryFolder()
    private val loja = ViewModelStore()
    private val guardado = GuardadoNaMemoria()
    private val conectado = MutableStateFlow(true)
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

    private fun lugares(servico: ServicoDeEstiloDeVida = rede.estiloDeVida): LugaresViewModel =
        ViewModelProvider.create(
            loja,
            viewModelFactory {
                initializer { LugaresViewModel(servico, CacheDeRespostas(guardado, DIA_DAS_CAPTURAS), rede.sessao, DIA_DAS_CAPTURAS, conectado) }
            },
        )["vm${criados++}", LugaresViewModel::class]

    private fun pedidos(inicio: String) = fixas.pedidos.filter { it.startsWith(inicio) }

    @Test
    fun `lista os lugares e os marcadores juntos`() = runTest {
        entrar()
        val estado = lugares().estado.esperar { it.lugares != null }
        assertEquals(listOf("Cantina da Nonna", "Café do Mirante", "Parque Ibirapuera"), estado.lugares!!.map { it.nome })
        assertEquals(listOf("Romântico", "Barato", "Com as crianças", "Vista bonita"), estado.dados!!.marcadores)
        assertEquals("BRL", estado.moeda)
        assertFalse(estado.formulario.aberto)
        assertNull(estado.guardadoEm)
    }

    @Test
    fun `pede o nome e cria com a data de hoje, nota 5 e os marcadores escolhidos`() = runTest {
        entrar()
        val lugares = lugares()
        lugares.estado.esperar { it.lugares != null }
        lugares.abrirNovo()
        lugares.estado.value.formulario.let {
            assertTrue(it.aberto)
            assertEquals("2026-10-28", it.data)
            assertEquals(5, it.nota)
        }
        lugares.mudarNome("   ")
        lugares.salvar()
        assertEquals(CampoDoLugar.Nome, lugares.estado.value.formulario.faltou)
        assertEquals(MensagemDeErro.DoApp(R.string.lugares_falta_nome), lugares.estado.value.formulario.erro)
        assertTrue(pedidos("POST /api/places").isEmpty())

        lugares.mudarNome(" Pizzaria ")
        lugares.mudarLocalizacao("  ")
        lugares.mudarNota(3)
        lugares.alternarMarcador("Barato")
        lugares.alternarMarcador("Romântico")
        lugares.alternarMarcador("Barato")
        lugares.mudarValor(BigDecimal("89.9"))
        lugares.salvar()
        val depois = lugares.estado.esperar { e -> e.lugares?.firstOrNull()?.nome == "Pizzaria" && !e.formulario.aberto }
        assertEquals(
            """POST /api/places {"nome":"Pizzaria","nota":3,"data":"2026-10-28","marcacoes":["Romântico"],"valor":89.9}""",
            pedidos("POST /api/places").single(),
        )
        assertEquals(FormularioDeLugar(), depois.formulario)
        assertEquals(4, depois.lugares!!.size)
    }

    @Test
    fun `edita com os dados do lugar e manda o valor zero quando fica vazio`() = runTest {
        entrar()
        val lugares = lugares()
        val parque = lugares.estado.esperar { it.lugares != null }.lugares!!.first { it.nome == "Parque Ibirapuera" }
        lugares.editar(parque)
        lugares.estado.value.formulario.let {
            assertEquals(parque, it.editando)
            assertEquals("Passeio", it.categoria)
            assertEquals(4, it.nota)
            assertNull(it.valor) // R$ 0 aparece vazio, como no site
            assertEquals(listOf("Barato", "Com as crianças"), it.marcacoes)
        }
        lugares.mudarComentario("Piquenique e bicicleta")
        lugares.salvar()
        val depois = lugares.estado.esperar { e -> e.lugares?.any { it.comentario == "Piquenique e bicicleta" } == true }
        val pedido = pedidos("PUT /api/places/${parque.id}").single()
        assertTrue(pedido, pedido.contains(""""valor":0"""))
        assertFalse(pedido.contains("imageBase64"))
        // Fica no mesmo lugar da lista.
        assertEquals(parque.id, depois.lugares!![2].id)
    }

    @Test
    fun `a foto e reduzida e vai junto, e o lugar volta com o fotoAssetId`() = runTest {
        entrar()
        val lugares = lugares()
        lugares.estado.esperar { it.lugares != null }
        lugares.abrirNovo()
        val camera = fotoGrande(3200, 2400)
        lugares.escolherFoto { ByteArrayInputStream(camera) }
        val escolhida = lugares.estado.esperar { it.formulario.foto != null }.formulario
        assertFalse(escolhida.lendoFoto)
        val jpeg = Base64.getDecoder().decode(escolhida.foto)
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, limites)
        assertEquals(1600, limites.outWidth)
        assertEquals("image/jpeg", limites.outMimeType)

        lugares.mudarNome("Praia")
        lugares.salvar()
        val depois = lugares.estado.esperar { e -> e.lugares?.firstOrNull()?.nome == "Praia" }
        val pedido = pedidos("POST /api/places").single()
        assertTrue(pedido.contains(""""imageBase64":"${escolhida.foto}""""))
        assertTrue(pedido.contains(""""mimeType":"image/jpeg""""))
        assertEquals(true, depois.lugares!!.first().temFoto)
        assertNotNull(depois.lugares!!.first().fotoAssetId)
    }

    @Test
    fun `imagem que nao abre avisa e nao guarda foto`() = runTest {
        entrar()
        val lugares = lugares()
        lugares.estado.esperar { it.lugares != null }
        lugares.abrirNovo()
        lugares.escolherFoto { ByteArrayInputStream("<svg/>".toByteArray()) }
        val depois = lugares.estado.esperar { it.formulario.erro != null }.formulario
        assertEquals(MensagemDeErro.DoApp(R.string.lugares_foto_ilegivel), depois.erro)
        assertNull(depois.foto)
    }

    @Test
    fun `cria marcador so com duas letras ou mais`() = runTest {
        entrar()
        val lugares = lugares()
        lugares.estado.esperar { it.lugares != null }
        lugares.abrirNovo()
        lugares.mudarNovoMarcador(" a ")
        lugares.criarMarcador()
        assertEquals(CampoDoLugar.Marcador, lugares.estado.value.formulario.faltou)
        assertEquals(MensagemDeErro.DoApp(R.string.lugares_marcador_invalido), lugares.estado.value.formulario.erro)
        assertTrue(pedidos("POST /api/places/tags").isEmpty())

        lugares.mudarNovoMarcador(" Pet friendly ")
        lugares.criarMarcador()
        val depois = lugares.estado.esperar { "Pet friendly" in it.dados!!.marcadores }
        assertEquals("""POST /api/places/tags {"nome":"Pet friendly"}""", pedidos("POST /api/places/tags").single())
        assertEquals("", depois.formulario.novoMarcador)
        assertNull(depois.formulario.erro)
    }

    @Test
    fun `apaga so depois de confirmar`() = runTest {
        entrar()
        val lugares = lugares()
        val cantina = lugares.estado.esperar { it.lugares != null }.lugares!!.first()
        lugares.pedirParaApagar(cantina)
        assertEquals(cantina, lugares.estado.value.apagando)
        lugares.desistir()
        assertTrue(pedidos("DELETE").isEmpty())

        lugares.pedirParaApagar(cantina)
        lugares.confirmar()
        val depois = lugares.estado.esperar { e -> e.lugares!!.none { it.id == cantina.id } }
        assertNull(depois.apagando)
        assertEquals(listOf("DELETE /api/places/${cantina.id}"), pedidos("DELETE"))
    }

    @Test
    fun `abrir no mapa procura o nome com a localizacao, e sem app de mapas avisa`() = runTest {
        entrar()
        val lugares = lugares()
        val cantina = lugares.estado.esperar { it.lugares != null }.lugares!!.first()
        assertEquals("geo:0,0?q=Cantina%20da%20Nonna%2C%20Vila%20Madalena%2C%20S%C3%A3o%20Paulo", enderecoNoMapa(cantina))
        assertNull(enderecoNoMapa(cantina.copy(localizacao = " ")))
        assertNull(enderecoNoMapa(cantina.copy(localizacao = null)))
        lugares.semAppDeMapa()
        assertEquals(MensagemDeErro.DoApp(R.string.lugares_sem_mapa), lugares.estado.value.erro)
    }

    @Test
    fun `erro ao salvar fica no formulario aberto`() = runTest {
        entrar()
        val recusa = object : ServicoDeEstiloDeVida by rede.estiloDeVida {
            override suspend fun criarLugar(lugar: br.com.piggu.core.model.NovoLugar): Lugar =
                throw ErroDaApi.DoServidor(400, "VALIDACAO", "Nome grande demais.", emptyList(), null)
        }
        val lugares = lugares(recusa)
        lugares.estado.esperar { it.lugares != null }
        lugares.abrirNovo()
        lugares.mudarNome("Bar")
        lugares.salvar()
        val falhou = lugares.estado.esperar { it.formulario.erro != null && !it.formulario.salvando }
        assertTrue(falhou.formulario.aberto)
        assertEquals(MensagemDeErro.DoServidor("Nome grande demais."), falhou.formulario.erro)
        assertNull(falhou.formulario.faltou)
    }

    @Test
    fun `sem internet mostra o guardado com a hora, ou o erro quando nao ha nada`() = runTest {
        entrar()
        val semRede = object : ServicoDeEstiloDeVida by rede.estiloDeVida {
            override suspend fun listarLugares(): List<Lugar> = throw ErroDaApi.SemInternet(IOException("sem rede"))
        }
        conectado.value = false
        val vazio = lugares(semRede).estado.esperar { it.carga is Carga.Falhou }
        assertEquals(FalhaAoCarregar.SemInternet, falhaDe((vazio.carga as Carga.Falhou).erro))

        conectado.value = true
        lugares().estado.esperar { it.lugares != null }
        assertNotNull(guardado.respostas["lugares"])
        conectado.value = false
        val offline = lugares(semRede).estado.esperar { it.guardadoEm != null }
        assertEquals(3, offline.lugares!!.size)
        assertEquals(4, offline.dados!!.marcadores.size)
        assertFalse(offline.conectado)
    }
}
