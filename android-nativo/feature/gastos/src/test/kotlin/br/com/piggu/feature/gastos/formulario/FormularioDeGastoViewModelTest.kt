package br.com.piggu.feature.gastos.formulario

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Lancamentos
import br.com.piggu.core.data.ListasParaLancar
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.data.VERSAO_DOS_TERMOS
import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.model.Gasto
import br.com.piggu.core.model.JsonDoPiggu
import br.com.piggu.core.model.NovoLancamento
import br.com.piggu.core.model.PedidoDeLeitura
import br.com.piggu.core.model.PigguRole
import br.com.piggu.core.model.Plano
import br.com.piggu.core.model.ReciboLido
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.core.testing.DIA_DAS_CAPTURAS
import br.com.piggu.core.testing.FilaNaMemoria
import br.com.piggu.core.testing.RespostaFixa
import br.com.piggu.core.testing.RespostasFixas
import br.com.piggu.core.testing.esperar
import br.com.piggu.core.testing.redeDeTeste
import br.com.piggu.feature.gastos.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigDecimal

/** Uma foto de câmera, em JPEG. */
private fun fotoDeCamera(largura: Int, altura: Int): ByteArray =
    ByteArrayOutputStream().also { Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()

/** formulario-de-gasto.spec.ts, com a rede de verdade respondida pela API falsa (Ana, BRL, Nubank e Itaú). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FormularioDeGastoViewModelTest {
    @get:Rule val pasta = TemporaryFolder()
    private val contexto: Context = ApplicationProvider.getApplicationContext()

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())
    // Sem resetMain: resposta atrasada da API falsa (outra thread) iria para um Main desfeito e derrubava o teste seguinte no CI.
    private object SemCache : RespostasGuardadasDao {
        override suspend fun ler(chave: String): RespostaGuardada? = null
        override suspend fun guardar(resposta: RespostaGuardada) = Unit
        override suspend fun apagarTudo() = Unit
    }

    private fun conectar(conectado: Boolean) {
        val conectividade = contexto.getSystemService(ConnectivityManager::class.java)
        val capacidades = ShadowNetworkCapabilities.newInstance()
        shadowOf(capacidades).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (conectado) shadowOf(capacidades).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowOf(conectividade).setNetworkCapabilities(conectividade.activeNetwork, capacidades)
    }

    private val fila = FilaNaMemoria()
    private val lancamentos = Lancamentos()

    private suspend fun TestScope.abrir(
        fixas: RespostasFixas = RespostasFixas(),
        conectado: Boolean = true,
        gratuito: Boolean = false,
        financas: (ServicoDeFinancas) -> ServicoDeFinancas = { it },
    ): FormularioDeGastoViewModel {
        conectar(conectado)
        val rede = redeDeTeste(pasta.root, fixas)
        rede.entrar()
        if (gratuito) rede.sessao.entrou(rede.sessao.usuario!!.copy(plano = Plano.GRATUITO, role = PigguRole.TITULAR))
        val listas = ListasParaLancar(CacheDeRespostas(SemCache, DIA_DAS_CAPTURAS), rede.financas, rede.identidade)
        val viewModel = FormularioDeGastoViewModel(
            contexto, rede.sessao, financas(rede.financas), listas, fila, MonitorDeRede(contexto, backgroundScope), lancamentos, DIA_DAS_CAPTURAS,
        )
        viewModel.estado.esperar { it.contas.isNotEmpty() && it.membros.isNotEmpty() && it.categorias.isNotEmpty() }
        return viewModel
    }

    private fun RespostasFixas.lancados(): List<NovoLancamento> = pedidos
        .filter { it.startsWith("POST /api/expenses ") }
        .map { JsonDoPiggu.decodeFromString(NovoLancamento.serializer(), it.substringAfter("POST /api/expenses ")) }

    private suspend fun <T> ReceiveTurbine<T>.ate(condicao: (T) -> Boolean): T {
        while (true) awaitItem().let { if (condicao(it)) return it }
    }

    private fun FormularioDeGastoViewModel.preencher(item: String, valor: String, linha: Int = 0) {
        val chave = estado.value.itens[linha].chave
        mudarDescricao(chave, item)
        mudarValor(chave, BigDecimal(valor))
    }

    private fun FormularioDeGastoViewModel.erro() = (estado.value.erro as? MensagemDeErro.DoApp)?.texto

    @Test
    fun `aponta o que falta - item, valor, conta, cartao e parcelas - sem chamar o servidor`() = runTest {
        val fixas = RespostasFixas()
        val viewModel = abrir(fixas)
        assertEquals("BRL", viewModel.estado.value.moeda)
        // Débito com uma conta só: ela já vem escolhida.
        assertEquals("conta-itau", viewModel.estado.value.contaId)

        viewModel.lancar()
        assertEquals(R.string.formulario_digite_o_item, viewModel.erro())
        assertEquals(viewModel.estado.value.itens[0].chave, viewModel.estado.value.itemComErro)

        viewModel.mudarDescricao(viewModel.estado.value.itens[0].chave, "Leite")
        viewModel.lancar()
        assertEquals(R.string.formulario_valor_invalido, viewModel.erro())
        viewModel.mudarValor(viewModel.estado.value.itens[0].chave, BigDecimal("1000000.01"))
        viewModel.lancar()
        assertEquals(R.string.formulario_valor_invalido, viewModel.erro())
        viewModel.mudarValor(viewModel.estado.value.itens[0].chave, BigDecimal("8.50"))

        viewModel.escolherConta("")
        viewModel.lancar()
        assertEquals(R.string.formulario_escolha_a_conta, viewModel.erro())
        assertEquals(CampoQueFaltou.CONTA, viewModel.estado.value.faltou)

        viewModel.trocarForma(FormaDePagamento.CARTAO)
        assertEquals("conta-nubank", viewModel.estado.value.contaId)
        viewModel.escolherConta("")
        viewModel.lancar()
        assertEquals(R.string.formulario_escolha_o_cartao, viewModel.erro())

        viewModel.escolherConta("conta-nubank")
        viewModel.trocarModo(ModoDaCompra.PARCELADO)
        assertEquals("2", viewModel.estado.value.parcelas)
        for (parcelas in listOf("49", "1", "")) {
            viewModel.mudarParcelas(parcelas)
            viewModel.lancar()
            assertEquals(R.string.formulario_parcelas_invalidas, viewModel.erro())
            assertEquals(CampoQueFaltou.PARCELAS, viewModel.estado.value.faltou)
        }
        assertEquals(emptyList<NovoLancamento>(), fixas.lancados())
    }

    @Test
    fun `cartao sem cadastro pede para cadastrar`() = runTest {
        val fixas = RespostasFixas(
            "GET /api/accounts" to RespostaFixa(200, """[{"id":"conta-itau","nome":"Conta Itaú","tipo":"CONTA"}]"""),
        )
        val viewModel = abrir(fixas)
        viewModel.preencher("Leite", "8.50")
        viewModel.trocarForma(FormaDePagamento.CARTAO)
        viewModel.lancar()
        assertEquals(R.string.formulario_cadastre_o_cartao, viewModel.erro())
    }

    @Test
    fun `varios itens parcelados no cartao vao num lancamento so e avisam as telas`() = runTest {
        val fixas = RespostasFixas()
        val viewModel = abrir(fixas)
        viewModel.preencher("Tênis", "300")
        viewModel.adicionarItem()
        viewModel.adicionarItem()
        viewModel.preencher("Meia", "30", linha = 1)
        viewModel.removerItem(viewModel.estado.value.itens[2].chave)
        viewModel.mudarCategoria(viewModel.estado.value.itens[1].chave, "Vestuário")
        viewModel.mudarEstabelecimento("  Loja do centro ")
        viewModel.trocarForma(FormaDePagamento.CARTAO)
        viewModel.trocarModo(ModoDaCompra.PARCELADO)
        viewModel.mudarParcelas("3")
        assertEquals(BigDecimal("110.00"), viewModel.estado.value.valorDaParcela)
        // Compra em 28/10 no Nubank, que fecha dia 5: fatura de novembro, vencendo 12/11.
        assertEquals("2026-11" to "2026-11-12", viewModel.estado.value.fatura)

        viewModel.estado.test {
            viewModel.lancar()
            val lancado = ate { it.lancado != null }
            assertTrue(lancado.lancado!!.texto in setOf(R.string.formulario_gasto_lancado, R.string.formulario_lancado_atencao, R.string.formulario_lancado_estourou))
            // Limpo para o próximo, com a conta lembrada.
            assertEquals(1, lancado.itens.size)
            assertEquals(ModoDaCompra.VISTA, lancado.modo)
            assertEquals("", lancado.estabelecimento)
            cancelAndIgnoreRemainingEvents()
        }
        val pedido = fixas.lancados().single()
        assertEquals(listOf("Tênis", "Meia"), pedido.itens.map { it.item })
        assertEquals(listOf(null, "Vestuário"), pedido.itens.map { it.categoria })
        assertEquals(listOf("Variavel", "Variavel"), pedido.itens.map { it.tipo })
        assertEquals(3, pedido.parcelas)
        assertNull(pedido.recorrente)
        assertEquals("conta-nubank", pedido.contaId)
        assertEquals("Loja do centro", pedido.estabelecimento)
        assertEquals("2026-10-28", pedido.data)
        assertEquals("Manual", pedido.origem)
        assertEquals(1, lancamentos.versao.value)
        viewModel.avisoEntregue()
        assertNull(viewModel.estado.value.lancado)
    }

    @Test
    fun `recorrente vira gasto fixo e debito volta a vista`() = runTest {
        val fixas = RespostasFixas()
        val viewModel = abrir(fixas)
        viewModel.trocarForma(FormaDePagamento.CARTAO)
        viewModel.trocarModo(ModoDaCompra.RECORRENTE)
        viewModel.trocarForma(FormaDePagamento.DEBITO)
        assertEquals(ModoDaCompra.VISTA, viewModel.estado.value.modo)
        viewModel.trocarForma(FormaDePagamento.CARTAO)
        viewModel.trocarModo(ModoDaCompra.RECORRENTE)
        assertEquals(28, viewModel.estado.value.diaDaCompra)
        viewModel.preencher("Streaming", "39.90")
        viewModel.lancar()
        viewModel.estado.esperar { it.lancado != null }
        val pedido = fixas.lancados().single()
        assertEquals(true, pedido.recorrente)
        assertEquals("Fixo", pedido.itens.single().tipo)
        assertNull(pedido.parcelas)
    }

    @Test
    fun `compra em outra moeda espera a cotacao e grava convertida`() = runTest {
        val fixas = RespostasFixas()
        val viewModel = abrir(fixas)
        viewModel.preencher("Café", "10")
        viewModel.trocarMoeda("USD")
        // Na demo, 1 USD = 5,5 BRL.
        viewModel.estado.esperar { it.cotacao != null }
        assertEquals(BigDecimal("55.00"), viewModel.estado.value.totalConvertido)
        viewModel.lancar()
        viewModel.estado.esperar { it.lancado != null }
        val item = fixas.lancados().single().itens.single()
        assertEquals(BigDecimal("55.00"), item.valor)
        assertEquals("USD", item.moedaOriginal)
        assertEquals(BigDecimal("10"), item.valorOriginal)
        assertTrue(fixas.pedidos.any { it.startsWith("GET /api/exchange-rate") })
    }

    @Test
    fun `sem cotacao nao lanca em outra moeda`() = runTest {
        val fixas = RespostasFixas("GET /api/exchange-rate" to RespostasFixas.erro(503, "CAMBIO", "Cotação indisponível."))
        val viewModel = abrir(fixas)
        viewModel.preencher("Café", "10")
        viewModel.trocarMoeda("USD")
        assertEquals(MensagemDeErro.DoServidor("Cotação indisponível."), viewModel.estado.esperar { it.erro != null }.erro)
        viewModel.lancar()
        assertEquals(R.string.formulario_aguarde_a_cotacao, viewModel.erro())
        viewModel.trocarMoeda("BRL")
        assertEquals(false, viewModel.estado.value.outraMoeda)
    }

    @Test
    fun `dividir e compartilhar vao no pedido`() = runTest {
        val fixas = RespostasFixas()
        val viewModel = abrir(fixas)
        val outra = viewModel.estado.value.membros.last().id
        assertTrue(viewModel.estado.value.podeDividir)
        assertEquals(false, viewModel.estado.value.compartilhadoEfetivo)
        viewModel.alternarDivisao(outra)
        viewModel.alternarDivisao(outra)
        viewModel.alternarDivisao(outra)
        viewModel.mudarCompartilhado(true)
        viewModel.preencher("Pizza", "80")
        viewModel.lancar()
        viewModel.estado.esperar { it.lancado != null }
        val pedido = fixas.lancados().single()
        assertEquals(listOf(outra), pedido.dividirCom)
        assertEquals(true, pedido.compartilhado)
        // Limpou a escolha: o próximo volta ao padrão.
        assertEquals(emptyList<String>(), viewModel.estado.value.dividirCom)
        assertNull(viewModel.estado.value.compartilhado)
    }

    @Test
    fun `avisa quando o preco passou da media do item`() = runTest {
        val fixas = RespostasFixas(
            "GET /api/products/price-check" to RespostaFixa(200, """{"media":10.00,"compras":3,"percentual":50,"acima":true}"""),
        )
        val viewModel = abrir(fixas)
        val chave = viewModel.estado.value.itens[0].chave
        viewModel.conferirPreco(chave)
        assertNull(viewModel.estado.value.itens[0].precoAcima)

        viewModel.preencher("Leite", "15")
        viewModel.conferirPreco(chave)
        val preco = viewModel.estado.esperar { it.itens[0].precoAcima != null }.itens[0].precoAcima!!
        assertEquals(BigDecimal("10.00"), preco.media)
        assertTrue(fixas.pedidos.any { it == "GET /api/products/price-check" })

        // Em outra moeda não confere.
        viewModel.trocarMoeda("USD")
        viewModel.conferirPreco(chave)
        assertNull(viewModel.estado.value.itens[0].precoAcima)
    }

    @Test
    fun `cria a categoria nova e devolve o erro do servidor`() = runTest {
        val viewModel = abrir()
        val lista = viewModel.criarCategoria("Pet").getOrThrow()
        assertTrue("Pet" in lista)
        viewModel.categoriasCriadas(lista)
        assertTrue("Pet" in viewModel.estado.value.categorias)
        val falha = viewModel.criarCategoria("Pet").exceptionOrNull()
        assertEquals(422, (falha as ErroDaApi.DoServidor).status)
    }

    @Test
    fun `sem rede no aparelho o gasto vai direto para a fila`() = runTest {
        val fixas = RespostasFixas()
        val viewModel = abrir(fixas, conectado = false)
        assertTrue(viewModel.estado.value.semInternet)
        viewModel.preencher("Pão", "7")
        viewModel.lancar()
        val estado = viewModel.estado.esperar { it.lancado != null }
        assertEquals(R.string.formulario_guardado_sem_internet, estado.lancado!!.texto)
        assertEquals(emptyList<NovoLancamento>(), fixas.lancados())
        val guardado = fila.aguardando.value.single()
        assertEquals("Pão", guardado.lancamento.itens.single().item)
        assertEquals(guardado.idDoEnvio, guardado.lancamento.idDoEnvio)
    }

    @Test
    fun `sem resposta a fila guarda o mesmo idDoEnvio que foi no POST`() = runTest {
        val tentados = mutableListOf<NovoLancamento>()
        val viewModel = abrir { real ->
            object : ServicoDeFinancas by real {
                override suspend fun lancarGastos(pedido: NovoLancamento): List<Gasto> {
                    tentados += pedido
                    throw ErroDaApi.SemInternet(IOException("sem sinal"))
                }
            }
        }
        viewModel.preencher("Pão", "7")
        viewModel.lancar()
        assertEquals(R.string.formulario_guardado_sem_internet, viewModel.estado.esperar { it.lancado != null }.lancado!!.texto)
        assertEquals(tentados.single().idDoEnvio, fila.aguardando.value.single().idDoEnvio)
        assertEquals(0, lancamentos.versao.value)
    }

    @Test
    fun `erro do servidor aparece e cada toque em Lancar leva um idDoEnvio novo`() = runTest {
        val fixas = RespostasFixas("POST /api/expenses" to RespostasFixas.erro(500, "ERRO", "O servidor tropeçou."))
        val viewModel = abrir(fixas)
        viewModel.preencher("Pão", "7")
        repeat(2) {
            viewModel.lancar()
            viewModel.estado.esperar { !it.salvando && it.erro != null }
        }
        assertEquals(MensagemDeErro.DoServidor("O servidor tropeçou."), viewModel.estado.value.erro)
        val ids = fixas.lancados().map { it.idDoEnvio }
        assertEquals(2, ids.size)
        assertNotEquals(ids[0], ids[1])
        assertTrue(fila.aguardando.value.isEmpty())
    }

    @Test
    fun `recibo lido fica em conferencia e so preenche o formulario ao confirmar`() = runTest {
        val fixas = RespostasFixas()
        val viewModel = abrir(fixas)
        // Premium: o contador não é pedido.
        assertTrue(fixas.pedidos.none { it.startsWith("GET /api/receipts/usage") })
        viewModel.lerFotoDoRecibo { ByteArrayInputStream(fotoDeCamera(3200, 2400)) }
        val lendo = viewModel.estado.esperar { it.recibo != null }
        assertFalse(lendo.lendoRecibo)
        assertEquals(4, lendo.itensDoRecibo.size)
        assertEquals(BigDecimal("58.08"), lendo.totalDoRecibo)
        // Nada gravado nem preenchido ainda; a foto foi reduzida e sem autorização da IA.
        assertEquals(emptyList<NovoLancamento>(), fixas.lancados())
        assertEquals("", viewModel.estado.value.itens.single().item)
        val leitura = fixas.pedidos.single { it.startsWith("POST /api/receipts/parse") }
        assertTrue(leitura.contains(""""mimeType":"image/jpeg""""))
        assertFalse(leitura.contains("autorizoIa"))
        val jpeg = lendo.fotoDoRecibo!!
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, limites)
        assertEquals(1600, limites.outWidth)

        // Editar e tirar na conferência.
        val itens = lendo.itensDoRecibo
        viewModel.mudarDescricao(itens[0].chave, "Arroz integral")
        viewModel.mudarValor(itens[1].chave, BigDecimal("9.00"))
        viewModel.removerItem(itens[3].chave)
        assertEquals(listOf("Arroz integral", "Feijão carioca 1kg", "Café 500g"), viewModel.estado.value.itensDoRecibo.map { it.item })
        assertEquals("", viewModel.estado.value.itens.single().item)

        viewModel.confirmarRecibo()
        val preenchido = viewModel.estado.value
        assertNull(preenchido.recibo)
        assertNull(preenchido.fotoDoRecibo)
        assertEquals(listOf("Arroz integral", "Feijão carioca 1kg", "Café 500g"), preenchido.itens.map { it.item })
        assertEquals(listOf("Alimentação", "Alimentação", "Alimentação"), preenchido.itens.map { it.categoria })
        assertEquals("Supermercado Bom Preço", preenchido.estabelecimento)
        assertEquals(java.time.LocalDate.of(2026, 10, 28), preenchido.data)

        viewModel.lancar()
        viewModel.estado.esperar { it.lancado != null }
        val pedido = fixas.lancados().single()
        assertEquals("Foto", pedido.origem)
        assertEquals(lendo.recibo!!.reciboId, pedido.reciboId)
        assertEquals(BigDecimal("9.00"), pedido.itens[1].valor)
        // O próximo volta a ser manual.
        assertNull(viewModel.estado.value.reciboId)
    }

    @Test
    fun `conferencia sem item valido nao confirma e descartar volta ao formulario`() = runTest {
        val viewModel = abrir()
        viewModel.lerFotoDoRecibo { ByteArrayInputStream(fotoDeCamera(800, 600)) }
        viewModel.estado.esperar { it.recibo != null }.itensDoRecibo.forEach { viewModel.removerItem(it.chave) }
        viewModel.confirmarRecibo()
        assertEquals(R.string.recibo_nenhum_item_valido, viewModel.erro())
        assertTrue(viewModel.estado.value.recibo != null)
        viewModel.descartarRecibo()
        assertNull(viewModel.estado.value.recibo)
        assertNull(viewModel.estado.value.fotoDoRecibo)
        assertEquals(1, viewModel.estado.value.itens.size)
    }

    @Test
    fun `foto que nao e imagem avisa sem chamar o servidor`() = runTest {
        val fixas = RespostasFixas()
        val viewModel = abrir(fixas)
        viewModel.lerFotoDoRecibo { ByteArrayInputStream("não é foto".toByteArray()) }
        viewModel.estado.esperar { !it.lendoRecibo && it.erro != null }
        assertEquals(R.string.recibo_foto_ilegivel, viewModel.erro())
        assertTrue(fixas.pedidos.none { it.contains("/api/receipts/parse") })
    }

    @Test
    fun `sem autorizacao pergunta antes de ir a IA - recusar nao envia e autorizar grava a versao`() = runTest {
        val leituras = mutableListOf<PedidoDeLeitura>()
        val viewModel = abrir { real ->
            object : ServicoDeFinancas by real {
                override suspend fun lerRecibo(pedido: PedidoDeLeitura): ReciboLido {
                    leituras += pedido
                    if (pedido.autorizoIa != true) {
                        throw ErroDaApi.DoServidor(422, "CONSENTIMENTO_NECESSARIO", "Autorize o envio da foto ao Google.", emptyList(), null)
                    }
                    return real.lerRecibo(pedido)
                }
            }
        }
        val foto = fotoDeCamera(800, 600)
        viewModel.lerFotoDoRecibo { ByteArrayInputStream(foto) }
        val pedindo = viewModel.estado.esperar { it.pedidoDeIa != null }
        assertEquals(MensagemDeErro.DoServidor("Autorize o envio da foto ao Google."), pedindo.pedidoDeIa)
        assertNull(pedindo.erro)
        viewModel.recusarIa()
        assertNull(viewModel.estado.value.pedidoDeIa)
        assertNull(viewModel.estado.value.fotoDoRecibo)
        assertEquals(1, leituras.size)
        // Recusou: autorizar depois não manda nada.
        viewModel.autorizarIa()
        assertEquals(1, leituras.size)

        viewModel.lerFotoDoRecibo { ByteArrayInputStream(foto) }
        viewModel.estado.esperar { it.pedidoDeIa != null }
        viewModel.autorizarIa()
        viewModel.estado.esperar { it.recibo != null }
        assertEquals(3, leituras.size)
        assertEquals(true, leituras.last().autorizoIa)
        assertEquals(VERSAO_DOS_TERMOS, leituras.last().versaoDoAviso)
        assertEquals(leituras[1].imageBase64, leituras.last().imageBase64)
    }

    @Test
    fun `no gratuito mostra as leituras que sobram e o erro do servidor some com a foto`() = runTest {
        val fixas = RespostasFixas(
            "GET /api/receipts/usage" to RespostaFixa(200, """{"usadas":3,"limite":10,"restantes":7}"""),
            "POST /api/receipts/parse" to RespostasFixas.erro(422, "PLANO_PREMIUM", "As leituras grátis deste mês acabaram."),
        )
        val viewModel = abrir(fixas, gratuito = true)
        assertEquals(7, viewModel.estado.esperar { it.leiturasRestantes != null }.leiturasRestantes)
        viewModel.lerFotoDoRecibo { ByteArrayInputStream(fotoDeCamera(800, 600)) }
        val falhou = viewModel.estado.esperar { !it.lendoRecibo && it.erro != null }
        assertEquals(MensagemDeErro.DoServidor("As leituras grátis deste mês acabaram."), falhou.erro)
        assertNull(falhou.fotoDoRecibo)
        assertNull(falhou.pedidoDeIa)
    }

    @Test
    fun `o atalho preenche o valor e a fatura segue o fechamento`() = runTest {
        val viewModel = abrir()
        viewModel.preencherValor(BigDecimal("12.50"))
        assertEquals(BigDecimal("12.50"), viewModel.estado.value.itens[0].valor)
        viewModel.preencherValor(BigDecimal.ZERO)
        assertEquals(BigDecimal("12.50"), viewModel.estado.value.itens[0].valor)
        assertEquals("2026-10" to "2026-10-12", faturaDaCompra(5, 12, java.time.LocalDate.of(2026, 10, 4)))
        assertEquals("2026-03" to "2026-04-05", faturaDaCompra(31, 5, java.time.LocalDate.of(2026, 2, 28)))
    }
}
