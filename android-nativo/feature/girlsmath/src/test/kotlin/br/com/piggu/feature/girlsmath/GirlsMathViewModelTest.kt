package br.com.piggu.feature.girlsmath

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.ehAdmin
import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.model.ItemDoGirlsMath
import br.com.piggu.core.model.PigguRole
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeEstiloDeVida
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

private val EstadoDoGirlsMath.prontos: List<ItemDoGirlsMath>? get() = (itens as? Carga.Pronto)?.valor

private fun membro(role: String) = RespostaFixa(
    200,
    """{"accessToken":"a1","expiresIn":1800,"refreshToken":"r1","usuario":{"id":"u-bia","email":"bia@demo.piggu.app","nome":"Bia Souza",""" +
        """"primeiroNome":"Bia","role":"$role","ativo":true,"plano":"FREE","familia":"familia-demo"}}""",
)

/** Uma foto de câmera grande, em JPEG. */
private fun fotoGrande(largura: Int, altura: Int): ByteArray =
    ByteArrayOutputStream().also { Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        .toByteArray()

/**
 * Girls Math (girls-math.ts e girls-math.spec.ts), com a API falsa da demonstração: a bolsa de
 * R$ 349,90 em 03/10/2026 e o tênis de R$ 499 em setembro.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GirlsMathViewModelTest {
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

    private fun girlsMath(servico: ServicoDeEstiloDeVida = rede.estiloDeVida): GirlsMathViewModel =
        ViewModelProvider.create(
            loja,
            viewModelFactory {
                initializer {
                    GirlsMathViewModel(servico, rede.midia, CacheDeRespostas(guardado, DIA_DAS_CAPTURAS), rede.sessao, DIA_DAS_CAPTURAS, conectado)
                }
            },
        )["vm${criados++}", GirlsMathViewModel::class]

    private fun pedidos(inicio: String) = fixas.pedidos.filter { it.startsWith(inicio) }

    @Test
    fun `lista o que nao comprou com o placar do mes e o de sempre`() = runTest {
        entrar()
        val estado = girlsMath().estado.esperar { it.prontos != null }
        assertEquals(listOf("Bolsa de couro", "Tênis de corrida"), estado.lista.map { it.produto })
        assertEquals(0, estado.noMes.compareTo(BigDecimal("349.9")))
        assertEquals(0, estado.total.compareTo(BigDecimal("848.9")))
        assertEquals("2026-10-28", estado.hoje)
        assertTrue(estado.miniaturas.isEmpty())
        assertNull(estado.guardadoEm)
    }

    @Test
    fun `pede produto e valor, e guarda sem foto com a data de hoje`() = runTest {
        entrar()
        val girlsMath = girlsMath()
        girlsMath.estado.esperar { it.prontos != null }
        // A validação muda o estado na hora, sem ir à rede: lê o valor, sem contar emissões.
        girlsMath.guardar()
        girlsMath.estado.value.let {
            assertEquals(CampoDoGirlsMath.Produto, it.faltou)
            assertEquals(MensagemDeErro.DoApp(R.string.girlsmath_falta_produto), it.erro)
        }
        girlsMath.mudarProduto("  Vestido ")
        girlsMath.guardar()
        assertEquals(CampoDoGirlsMath.Valor, girlsMath.estado.value.faltou)
        assertTrue(pedidos("POST /api/girls-math").isEmpty())

        girlsMath.mudarLoja("   ")
        girlsMath.mudarValor(BigDecimal("150"))
        girlsMath.guardar()
        val depois = girlsMath.estado.esperar { e -> e.lista.firstOrNull()?.produto == "Vestido" && !e.salvando }
        assertEquals(
            """POST /api/girls-math {"produto":"Vestido","valor":150,"data":"2026-10-28"}""",
            pedidos("POST /api/girls-math").single(),
        )
        assertEquals("", depois.produto)
        assertNull(depois.valor)
        assertNull(depois.faltou)
        assertEquals(0, depois.noMes.compareTo(BigDecimal("499.9")))
        assertEquals(0, depois.total.compareTo(BigDecimal("998.9")))
    }

    @Test
    fun `a foto e reduzida e vai junto, e a miniatura vem do piggu-media`() = runTest {
        entrar()
        val girlsMath = girlsMath()
        girlsMath.estado.esperar { it.prontos != null }
        val camera = fotoGrande(3200, 2400)
        girlsMath.escolherFoto { ByteArrayInputStream(camera) }
        val escolhida = girlsMath.estado.esperar { it.foto != null }
        assertFalse(escolhida.lendoFoto)
        val jpeg = Base64.getDecoder().decode(escolhida.foto)
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, limites)
        assertEquals(1600, limites.outWidth)
        assertEquals(1200, limites.outHeight)
        assertEquals("image/jpeg", limites.outMimeType)

        girlsMath.removerFoto()
        assertNull(girlsMath.estado.value.foto)
        girlsMath.escolherFoto { ByteArrayInputStream(camera) }
        girlsMath.estado.esperar { it.foto != null }

        girlsMath.mudarProduto("Bolsa")
        girlsMath.mudarValor(BigDecimal("200"))
        girlsMath.guardar()
        val guardado = girlsMath.estado.esperar { e -> e.lista.first().produto == "Bolsa" && e.miniaturas.isNotEmpty() }
        val pedido = pedidos("POST /api/girls-math").single()
        assertTrue(pedido.contains(""""imageBase64":"${escolhida.foto}""""))
        assertTrue(pedido.contains(""""mimeType":"image/jpeg""""))
        assertNull(guardado.foto)
        val item = guardado.lista.first()
        assertEquals(true, item.temFoto)
        assertTrue(guardado.miniaturas.getValue(item.fotoAssetId!!).contentEquals(jpeg))
        assertEquals(listOf("GET /api/assets/${item.fotoAssetId}/content"), pedidos("GET /api/assets"))
    }

    @Test
    fun `a foto de celular em pe pelo EXIF sai em pe`() {
        val arquivo = pasta.newFile("camera.jpg").apply { writeBytes(fotoGrande(3200, 2400)) }
        ExifInterface(arquivo).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val jpeg = reduzirFoto({ arquivo.inputStream() })!!
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, limites)
        assertEquals(1200, limites.outWidth)
        assertEquals(1600, limites.outHeight)
        // Regravada sem o EXIF de origem (onde fica a localização).
        assertEquals(ExifInterface.ORIENTATION_UNDEFINED, ExifInterface(jpeg.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
    }

    @Test
    fun `imagem que nao abre avisa e nao guarda foto`() = runTest {
        entrar()
        val girlsMath = girlsMath()
        girlsMath.estado.esperar { it.prontos != null }
        girlsMath.escolherFoto { ByteArrayInputStream("<svg/>".toByteArray()) }
        val depois = girlsMath.estado.esperar { it.erro != null }
        assertEquals(MensagemDeErro.DoApp(R.string.girlsmath_foto_ilegivel), depois.erro)
        assertNull(depois.foto)
        assertNull(lerParaATela("<svg/>".toByteArray(), 100))
    }

    @Test
    fun `o membro usa sem foto, como qualquer pessoa`() = runTest {
        entrar(RespostasFixas("POST /api/auth/entrar" to membro("MEMBRO")))
        assertEquals(PigguRole.MEMBRO, rede.sessao.usuario!!.role)
        assertFalse(rede.sessao.usuario!!.ehAdmin)
        val girlsMath = girlsMath()
        girlsMath.estado.esperar { it.prontos != null }
        girlsMath.mudarProduto("Batom")
        girlsMath.mudarValor(BigDecimal("39.9"))
        girlsMath.guardar()
        val depois = girlsMath.estado.esperar { e -> e.lista.first().produto == "Batom" }
        assertEquals(false, depois.lista.first().temFoto)
        assertFalse(pedidos("POST /api/girls-math").single().contains("imageBase64"))
    }

    @Test
    fun `apaga so depois de confirmar e o placar acompanha`() = runTest {
        entrar()
        val girlsMath = girlsMath()
        val bolsa = girlsMath.estado.esperar { it.prontos != null }.lista.first()
        girlsMath.pedirParaApagar(bolsa)
        assertEquals(bolsa, girlsMath.estado.value.apagando)
        girlsMath.desistir()
        assertTrue(pedidos("DELETE").isEmpty())

        girlsMath.pedirParaApagar(bolsa)
        girlsMath.confirmar()
        val depois = girlsMath.estado.esperar { e -> e.lista.none { it.id == bolsa.id } }
        assertNull(depois.apagando)
        assertEquals(listOf("DELETE /api/girls-math/${bolsa.id}"), pedidos("DELETE"))
        assertEquals(0, depois.noMes.signum())
        assertEquals(0, depois.total.compareTo(BigDecimal("499")))
    }

    @Test
    fun `sem internet mostra o guardado com a hora, ou o erro quando nao ha nada`() = runTest {
        entrar()
        val semRede = object : ServicoDeEstiloDeVida by rede.estiloDeVida {
            override suspend fun listarGirlsMath(): List<ItemDoGirlsMath> = throw ErroDaApi.SemInternet(IOException("sem rede"))
        }
        conectado.value = false
        val vazio = girlsMath(semRede).estado.esperar { it.itens is Carga.Falhou }
        assertEquals(FalhaAoCarregar.SemInternet, falhaDe((vazio.itens as Carga.Falhou).erro))
        assertEquals(0, vazio.total.signum())

        conectado.value = true
        girlsMath().estado.esperar { it.prontos != null }
        assertNotNull(guardado.respostas["girls-math"])
        conectado.value = false
        val offline = girlsMath(semRede).estado.esperar { it.guardadoEm != null }
        assertEquals(2, offline.lista.size)
        assertFalse(offline.conectado)
        assertEquals(0, offline.total.compareTo(BigDecimal("848.9")))
    }
}
