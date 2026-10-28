package br.com.piggu.feature.gastos.lista

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.GastoAguardando
import br.com.piggu.core.data.Lancamentos
import br.com.piggu.core.data.ListasParaLancar
import br.com.piggu.core.data.RepositorioDoMes
import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.model.ItemDeGasto
import br.com.piggu.core.model.NovoLancamento
import br.com.piggu.core.testing.DIA_DAS_CAPTURAS
import br.com.piggu.core.testing.FilaNaMemoria
import br.com.piggu.core.testing.RedeDeTeste
import br.com.piggu.core.testing.RespostaFixa
import br.com.piggu.core.testing.RespostasFixas
import br.com.piggu.core.testing.esperar
import br.com.piggu.core.testing.redeDeTeste
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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
import java.math.BigDecimal

/** A exclusão vai pelo escopo do app, noutra thread: pergunta à API até a condição valer. */
private suspend fun ate(condicao: suspend () -> Boolean) = withContext(Dispatchers.Default) {
    withTimeout(5_000) { while (!condicao()) delay(20) }
}

/** O Room na memória: guarda de verdade, para o offline-first rodar como no app. */
private class RespostasNaMemoria : RespostasGuardadasDao {
    private val guardadas = mutableMapOf<String, RespostaGuardada>()
    override suspend fun ler(chave: String) = guardadas[chave]
    override suspend fun guardar(resposta: RespostaGuardada) { guardadas[resposta.chave] = resposta }
    override suspend fun apagarTudo() = guardadas.clear()
}

/** A tela de Gastos com a sessão de verdade e a API da demonstração (Ana, 28/10/2026). */
@OptIn(ExperimentalCoroutinesApi::class)
class GastosViewModelTest {
    @get:Rule val pasta = TemporaryFolder()

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())
    /** A rede responde noutra thread: o que ainda corre no viewModelScope para antes de devolver o Main. */
    @After fun devolver() {
        criados.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private lateinit var rede: RedeDeTeste
    private val criados = mutableListOf<GastosViewModel>()

    private suspend fun TestScope.tela(fixas: RespostasFixas = RespostasFixas(), fila: FilaNaMemoria = FilaNaMemoria()): GastosViewModel {
        rede = redeDeTeste(pasta.root, fixas)
        rede.entrar()
        val cache = CacheDeRespostas(RespostasNaMemoria(), DIA_DAS_CAPTURAS)
        val financas = rede.financas
        return GastosViewModel(
            rede.sessao, financas, RepositorioDoMes(cache, financas), ListasParaLancar(cache, financas, rede.identidade),
            fila, Lancamentos(), MutableStateFlow(true), DIA_DAS_CAPTURAS, backgroundScope,
        ).also { criados += it }
    }

    private suspend fun GastosViewModel.pronto() = estado.esperar { it.carga is Carga.Pronto && it.categorias.isNotEmpty() && it.contas.isNotEmpty() }

    private suspend fun GastosViewModel.gasto(item: String) = pronto().gastos!!.first { it.item == item }

    @Test
    fun `lista do mes agrupada por dia, do mais novo para o mais velho`() = runTest {
        val estado = tela().pronto()
        assertEquals("2026-10", estado.mes)
        val dias = estado.dias
        assertEquals(dias.map { it.dia }.sortedDescending(), dias.map { it.dia })
        assertEquals(dias.map { it.dia }.distinct(), dias.map { it.dia })
        dias.forEach { dia -> assertTrue(dia.gastos.all { it.gasto.data == dia.dia }) }
        assertEquals(estado.gastos!!.size, dias.sumOf { it.gastos.size })
        assertEquals(estado.gastos!!.fold(BigDecimal.ZERO) { s, g -> s + g.valor }, estado.total)
        assertEquals("Quarta-feira, 28 de outubro", diaPorExtenso("2026-10-28"))
    }

    @Test
    fun `busca sem acento e filtros de categoria e forma de pagamento`() = runTest {
        val viewModel = tela()
        viewModel.pronto()

        viewModel.mudarBusca("remedios")
        assertEquals(listOf("Remédios"), viewModel.estado.value.dias.flatMap { it.gastos }.map { it.gasto.item })
        assertTrue(viewModel.estado.value.filtrando)

        viewModel.limparFiltros()
        viewModel.filtrarPagamento(FiltroDePagamento.Cartao)
        val noCartao = viewModel.estado.value.dias.flatMap { it.gastos }.map { it.gasto }
        assertTrue(noCartao.isNotEmpty())
        assertTrue(noCartao.all { it.contaId == "conta-nubank" })

        viewModel.filtrarCategoria("Lazer")
        val lazerNoCartao = viewModel.estado.value.dias.flatMap { it.gastos }.map { it.gasto.item }.toSet()
        assertEquals(setOf("Jantar de sexta", "Cinema"), lazerNoCartao)
        assertEquals(0, BigDecimal("220").compareTo(viewModel.estado.value.total))

        viewModel.limparFiltros()
        viewModel.filtrarPagamento(FiltroDePagamento.SemConta)
        assertTrue(viewModel.estado.value.dias.flatMap { it.gastos }.all { it.gasto.contaId == null })
    }

    @Test
    fun `editar manda item, categoria e valor e sugere a regra quando a categoria mudou`() = runTest {
        val fixas = RespostasFixas()
        val viewModel = tela(fixas)
        val uber = viewModel.gasto("Uber Centro")

        viewModel.abrirEdicao(uber)
        viewModel.mudarValor(null)
        viewModel.salvarEdicao()
        assertNotNull(viewModel.estado.value.edicao?.erro)

        viewModel.mudarCategoria("Lazer")
        viewModel.mudarValor(BigDecimal("30.00"))
        viewModel.salvarEdicao()
        val depois = viewModel.estado.esperar { it.edicao == null }
        assertEquals(RegraSugerida("Uber Centro", "Lazer"), depois.sugestao)
        // Gasto da Ana sem mais ninguém na casa? A demo tem o Beto: o compartilhado vai.
        assertTrue(fixas.pedidos.any { it.startsWith("PUT /api/expenses/${uber.id} {\"item\":\"Uber Centro\",\"categoria\":\"Lazer\",\"valor\":30") })
        viewModel.estado.esperar { e -> e.gastos?.any { it.id == uber.id && it.categoria == "Lazer" } == true }
    }

    @Test
    fun `apagar some na hora, desfazer volta sem ir ao servidor, e a Snackbar sumida apaga`() = runTest {
        val fixas = RespostasFixas()
        val viewModel = tela(fixas)
        val padaria = viewModel.gasto("Padaria")

        viewModel.estado.test {
            // A divisão e as regras chegam por trás: vale o estado mais novo depois de cada evento.
            viewModel.excluir(padaria)
            val apagado = expectMostRecentItem()
            assertEquals(ExclusaoPendente(padaria, eAsProximas = false), apagado.exclusao)
            assertTrue(apagado.gastos!!.none { it.id == padaria.id })

            viewModel.desfazer()
            val desfeito = expectMostRecentItem()
            assertNull(desfeito.exclusao)
            assertTrue(desfeito.gastos!!.any { it.id == padaria.id })
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(fixas.pedidos.none { it.startsWith("DELETE") })

        viewModel.excluir(padaria)
        viewModel.confirmarExclusao()
        ate { rede.financas.listarGastos("2026-10").none { it.id == padaria.id } }
        assertTrue(fixas.pedidos.contains("DELETE /api/expenses/${padaria.id}"))
    }

    @Test
    fun `parcela pergunta e apaga tambem as proximas`() = runTest {
        val viewModel = tela()
        viewModel.mudarMes("2026-09")
        viewModel.estado.esperar { it.mes == "2026-09" && it.carga is Carga.Pronto }
        val fone = viewModel.gasto("Fone de ouvido")
        assertEquals(1, fone.seguintes())

        viewModel.excluir(fone)
        assertEquals(fone, viewModel.estado.value.confirmando)
        assertNull(viewModel.estado.value.exclusao)

        viewModel.apagar(fone, eAsProximas = true)
        assertNull(viewModel.estado.value.confirmando)
        viewModel.confirmarExclusao()
        // A parcela 3/3, de outubro, foi junto.
        ate { rede.financas.listarGastos("2026-10").none { it.item == "Fone de ouvido" } }
        assertTrue(rede.financas.listarGastos("2026-08").any { it.item == "Fone de ouvido" })
    }

    @Test
    fun `divisao do mes e quem deve a quem`() = runTest {
        val estado = tela().estado.esperar { it.transferencias.isNotEmpty() }
        assertEquals(setOf("u-ana", "u-beto"), estado.acerto.map { it.pessoa }.toSet())
        val transferencia = estado.transferencias.single()
        assertEquals("u-beto", transferencia.de)
        assertEquals("u-ana", transferencia.para)
        assertEquals(estado.acerto.first { it.pessoa == "u-ana" }.saldo, transferencia.valor)
    }

    @Test
    fun `regra criada aparece na lista e apagada some`() = runTest {
        val viewModel = tela()
        viewModel.pronto()
        viewModel.criarRegra(" ", "Pets")
        assertNotNull(viewModel.estado.value.erro)

        viewModel.mudarTermoDaRegra("Ração")
        viewModel.criarRegra(viewModel.estado.value.termoDaRegra, "Pets")
        val salva = viewModel.estado.esperar { e -> e.regras.any { it.termo == "racao" } }
        assertEquals("", salva.termoDaRegra)
        assertEquals(Mensagem(br.com.piggu.feature.gastos.R.string.regras_salva, listOf("racao", "Pets")), salva.aviso)

        viewModel.excluirRegra(salva.regras.first { it.termo == "racao" })
        viewModel.estado.esperar { e -> e.regras.none { it.termo == "racao" } }
    }

    @Test
    fun `link abre o mes pedido com o gasto em edicao e destacado`() = runTest {
        val viewModel = tela()
        viewModel.pronto()
        val remedio = rede.financas.listarGastos("2026-09").first { it.item == "Remédios" }

        viewModel.abrir(remedio.id, "2026-09")
        val aberto = viewModel.estado.esperar { it.edicao != null }
        assertEquals("2026-09", aberto.mes)
        assertEquals(remedio.id, aberto.edicao!!.gasto.id)
        assertEquals(remedio.id, aberto.destacado)

        viewModel.fecharEdicao()
        viewModel.abrir(remedio.id, null)
        assertEquals("2026-10", viewModel.estado.value.mes)
    }

    @Test
    fun `gasto da fila aparece no dia como aguardando e fora do total`() = runTest {
        val lancamento = NovoLancamento(
            data = "2026-10-27",
            itens = listOf(ItemDeGasto("Pão de queijo", valor = BigDecimal("12.00"))),
            idDoEnvio = "envio-1",
        )
        val fila = FilaNaMemoria(listOf(GastoAguardando("envio-1", lancamento, 0)), recusados = 2)
        val viewModel = tela(fila = fila)
        val estado = viewModel.pronto()
        val dia = estado.dias.first { it.dia == "2026-10-27" }
        val linha = dia.gastos.single { it.aguardando }
        assertEquals("Pão de queijo", linha.gasto.item)
        assertEquals(2, estado.recusados)
        assertEquals(estado.gastos!!.fold(BigDecimal.ZERO) { s, g -> s + g.valor }, estado.total)

        viewModel.esquecerRecusados()
        viewModel.estado.esperar { it.recusados == 0 }
    }

    @Test
    fun `previa do extrato vem com os ja importados desmarcados e importa so os marcados`() = runTest {
        val previa = """[
            {"data":"2026-10-10","descricao":"Uber","valor":23.5,"idExterno":"h1","categoria":"Transporte","jaImportada":false},
            {"data":"2026-10-09","descricao":"Velho","valor":5,"idExterno":"h0","categoria":"Outros","jaImportada":true}
        ]"""
        val fixas = RespostasFixas(
            "POST /api/expenses/import/preview" to RespostaFixa(200, previa),
            "POST /api/expenses/import" to RespostaFixa(200, """{"importados":1,"pulados":0}"""),
        )
        val viewModel = tela(fixas)
        viewModel.pronto()

        viewModel.lerExtrato { "data;descricao;valor\n10/10/2026;Uber;-23,50\n".byteInputStream() }
        val comPrevia = viewModel.estado.esperar { it.previa != null }
        assertEquals(listOf(true, false), comPrevia.previa!!.map { it.marcada })
        assertTrue(fixas.pedidos.any { it.startsWith("POST /api/expenses/import/preview") && "Uber" in it })

        viewModel.mudarCategoriaDaLinha(0, "Lazer")
        viewModel.importarMarcados()
        val importado = viewModel.estado.esperar { it.previa == null && !it.ocupado }
        assertEquals(Mensagem(br.com.piggu.feature.gastos.R.string.importar_importados, listOf(1)), importado.aviso)
        val envio = fixas.pedidos.single { it.startsWith("POST /api/expenses/import {") }
        assertTrue(envio, "\"idExterno\":\"h1\",\"categoria\":\"Lazer\"" in envio)
        assertFalse(envio, "h0" in envio)
    }

    @Test
    fun `extrato grande demais nem vai ao servidor, e o Latin-1 e relido`() = runTest {
        val fixas = RespostasFixas()
        val viewModel = tela(fixas)
        viewModel.pronto()
        viewModel.lerExtrato { ByteArray(TAMANHO_MAXIMO_DO_EXTRATO + 10).inputStream() }
        viewModel.estado.esperar { it.erro != null && !it.ocupado }
        assertTrue(fixas.pedidos.none { "import" in it })
        assertEquals("Padaria Pão", textoDoExtrato("Padaria Pão".toByteArray(Charsets.ISO_8859_1)))
    }
}
