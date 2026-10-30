package br.com.piggu.feature.fotos

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
import br.com.piggu.core.model.FotoDoFeed
import br.com.piggu.core.model.Plano
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeMidia
import br.com.piggu.core.testing.DIA_DAS_CAPTURAS
import br.com.piggu.core.testing.RedeDeTeste
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
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

private class GuardadoNaMemoria : RespostasGuardadasDao {
    val respostas = ConcurrentHashMap<String, RespostaGuardada>()
    override suspend fun ler(chave: String) = respostas[chave]
    override suspend fun guardar(resposta: RespostaGuardada) { respostas[resposta.chave] = resposta }
    override suspend fun apagarTudo() = respostas.clear()
}

private val EstadoDasFotos.pronto: Boolean get() = fotos is Carga.Pronto

/** Uma foto de câmera grande, em JPEG. */
private fun fotoGrande(largura: Int, altura: Int): ByteArray =
    ByteArrayOutputStream().also { Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        .toByteArray()

/**
 * Fotos do mês (feed.spec.ts), com a API falsa da demonstração (Ana, Premium): "Thor no parque"
 * em outubro, "Aniversário do Beto" em setembro e "Praia no feriado" em agosto.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FotosViewModelTest {
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

    private suspend fun TestScope.entrar(respostas: RespostasFixas = RespostasFixas()) {
        fixas = respostas
        rede = redeDeTeste(pasta.root, fixas)
        rede.entrar()
    }

    private fun TestScope.fotos(midia: ServicoDeMidia = rede.midia): FotosViewModel =
        ViewModelProvider.create(
            loja,
            viewModelFactory {
                initializer {
                    FotosViewModel(midia, CacheDeRespostas(guardado, DIA_DAS_CAPTURAS), rede.sessao, DIA_DAS_CAPTURAS, backgroundScope, conectado)
                }
            },
        )["vm${criados++}", FotosViewModel::class]

    private fun pedidos(inicio: String) = fixas.pedidos.filter { it.startsWith(inicio) }

    @Test
    fun `mostra as fotos do mes e trocar de mes busca as do outro`() = runTest {
        entrar()
        val fotos = fotos()
        val outubro = fotos.estado.esperar { it.pronto && it.premium }
        assertEquals("2026-10", outubro.mes)
        assertEquals(listOf("Thor no parque"), outubro.lista.map { it.legenda })
        assertNull(outubro.guardadoEm)

        fotos.mudarMes("2026-09")
        val setembro = fotos.estado.esperar { e -> e.lista.map { it.legenda } == listOf("Aniversário do Beto") }
        assertEquals("2026-09", setembro.mes)
        assertEquals("2026-10", setembro.mesAtual)
        assertEquals(2, pedidos("GET /api/feed").size)
    }

    @Test
    fun `envia a foto reduzida no mes aberto e coloca no topo`() = runTest {
        entrar()
        val fotos = fotos()
        fotos.estado.esperar { it.pronto && it.premium }
        val camera = fotoGrande(3200, 2400)
        fotos.enviar { ByteArrayInputStream(camera) }
        val depois = fotos.estado.esperar { it.lista.size == 2 && !it.enviando }
        assertNull(depois.erro)
        assertEquals("", depois.lista.first().legenda)

        val pedido = pedidos("POST /api/feed").single()
        assertTrue(pedido.contains(""""mesKey":"2026-10""""))
        assertTrue(pedido.contains(""""mimeType":"image/jpeg""""))
        val base64 = Regex(""""imageBase64":"([^"]+)"""").find(pedido)!!.groupValues[1]
        val jpeg = Base64.getDecoder().decode(base64)
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, limites)
        assertEquals(1600, limites.outWidth)
        assertEquals(1200, limites.outHeight)
    }

    @Test
    fun `imagem que nao abre avisa e nao envia`() = runTest {
        entrar()
        val fotos = fotos()
        fotos.estado.esperar { it.pronto && it.premium }
        fotos.enviar { ByteArrayInputStream("<svg/>".toByteArray()) }
        val depois = fotos.estado.esperar { it.erro != null }
        assertEquals(MensagemDeErro.DoApp(R.string.fotos_ilegivel), depois.erro)
        assertFalse(depois.enviando)
        assertTrue(pedidos("POST /api/feed").isEmpty())
    }

    @Test
    fun `sem Premium ve, legenda e apaga, mas enviar fica no convite`() = runTest {
        entrar()
        rede.sessao.entrou(rede.sessao.usuario!!.copy(plano = Plano.GRATUITO))
        val fotos = fotos()
        val estado = fotos.estado.esperar { it.pronto && !it.premium }
        assertEquals(1, estado.lista.size)

        fotos.enviar { ByteArrayInputStream(fotoGrande(10, 10)) }
        assertFalse(fotos.estado.value.enviando)
        assertTrue(pedidos("POST /api/feed").isEmpty())

        val thor = estado.lista.single()
        fotos.mudarLegenda(thor, "Thor feliz")
        fotos.estado.esperar { it.lista.single().legenda == "Thor feliz" }
        fotos.pedirParaApagar(thor)
        fotos.confirmar()
        fotos.estado.esperar { it.lista.isEmpty() }
    }

    @Test
    fun `a legenda e salva sozinha uma vez, com o texto final`() = runTest {
        entrar()
        val fotos = fotos()
        val thor = fotos.estado.esperar { it.pronto }.lista.single()
        for (texto in listOf("P", "Pô", "Pôr do sol")) fotos.mudarLegenda(thor, texto)
        // O campo mostra o que a pessoa escreve antes de salvar.
        assertEquals("Pôr do sol", fotos.estado.value.legendaDe(thor))

        val depois = fotos.estado.esperar { e -> e.lista.single().legenda == "Pôr do sol" && e.legendas.isEmpty() }
        assertEquals("Pôr do sol", depois.legendaDe(depois.lista.single()))
        assertEquals(listOf("""PATCH /api/feed/${thor.id}/caption {"legenda":"Pôr do sol"}"""), pedidos("PATCH"))

        // Mais de 300 letras: corta como o maxlength do site.
        fotos.mudarLegenda(depois.lista.single(), "a".repeat(400))
        assertEquals(300, fotos.estado.value.legendaDe(thor).length)
    }

    @Test
    fun `voltar ao texto salvo nao manda nada`() = runTest {
        entrar()
        val fotos = fotos()
        val thor = fotos.estado.esperar { it.pronto }.lista.single()
        fotos.mudarLegenda(thor, "Thor")
        fotos.mudarLegenda(thor, "Thor no parque")
        fotos.estado.esperar { it.legendas.isEmpty() }
        assertTrue(pedidos("PATCH").isEmpty())
    }

    @Test
    fun `apaga so depois de confirmar`() = runTest {
        entrar()
        val fotos = fotos()
        val thor = fotos.estado.esperar { it.pronto }.lista.single()
        fotos.pedirParaApagar(thor)
        assertEquals(thor, fotos.estado.value.apagando)
        fotos.desistir()
        assertNull(fotos.estado.value.apagando)
        assertTrue(pedidos("DELETE").isEmpty())

        fotos.pedirParaApagar(thor)
        fotos.confirmar()
        val depois = fotos.estado.esperar { it.lista.isEmpty() }
        assertNull(depois.apagando)
        assertEquals(listOf("DELETE /api/feed/${thor.id}"), pedidos("DELETE"))
    }

    @Test
    fun `apagar com erro mostra a mensagem e a foto fica`() = runTest {
        entrar()
        val semRede = object : ServicoDeMidia by rede.midia {
            override suspend fun excluirDoFeed(id: String) = throw ErroDaApi.SemInternet(IOException("sem rede"))
        }
        val fotos = fotos(semRede)
        val thor = fotos.estado.esperar { it.pronto }.lista.single()
        fotos.pedirParaApagar(thor)
        fotos.confirmar()
        val depois = fotos.estado.esperar { it.erro != null }
        assertEquals(listOf(thor), depois.lista)
    }

    @Test
    fun `sem internet mostra o guardado com a hora, ou o erro quando nao ha nada`() = runTest {
        entrar()
        val semRede = object : ServicoDeMidia by rede.midia {
            override suspend fun listarFeed(mes: String?): List<FotoDoFeed> = throw ErroDaApi.SemInternet(IOException("sem rede"))
        }
        conectado.value = false
        val vazio = fotos(semRede).estado.esperar { it.fotos is Carga.Falhou }
        assertEquals(FalhaAoCarregar.SemInternet, falhaDe((vazio.fotos as Carga.Falhou).erro))

        conectado.value = true
        fotos().estado.esperar { it.pronto }
        assertNotNull(guardado.respostas["fotos/2026-10"])
        conectado.value = false
        val offline = fotos(semRede).estado.esperar { it.guardadoEm != null }
        assertEquals(1, offline.lista.size)
        assertFalse(offline.conectado)
    }
}
