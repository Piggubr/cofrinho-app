package br.com.piggu.feature.calendario

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.Lancamentos
import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.model.Gasto
import br.com.piggu.core.model.Nota
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
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
import java.util.concurrent.ConcurrentHashMap

/** O Room do guardado offline, na memória. */
private class GuardadoNaMemoria : RespostasGuardadasDao {
    val respostas = ConcurrentHashMap<String, RespostaGuardada>()
    override suspend fun ler(chave: String) = respostas[chave]
    override suspend fun guardar(resposta: RespostaGuardada) { respostas[resposta.chave] = resposta }
    override suspend fun apagarTudo() = respostas.clear()
}

private class Montagem(val rede: RedeDeTeste, val fixas: RespostasFixas, private val loja: ViewModelStore) {
    val guardado = GuardadoNaMemoria()
    val lancamentos = Lancamentos()
    val conectado = MutableStateFlow(true)
    private val cache = CacheDeRespostas(guardado, DIA_DAS_CAPTURAS)
    private var criados = 0

    fun calendario(financas: ServicoDeFinancas = rede.financas): CalendarioViewModel =
        ViewModelProvider.create(loja, viewModelFactory { initializer { CalendarioViewModel(financas, cache, rede.sessao, lancamentos, DIA_DAS_CAPTURAS, conectado) } })[
            "vm${criados++}", CalendarioViewModel::class,
        ]

    fun pedidos(inicio: String) = fixas.pedidos.filter { it.startsWith(inicio) }
}

private suspend fun TestScope.montar(pasta: java.io.File, loja: ViewModelStore): Montagem {
    val fixas = RespostasFixas()
    return Montagem(redeDeTeste(pasta, fixas), fixas, loja).also { it.rede.entrar() }
}

private val EstadoDoCalendario.carregado: DadosDoCalendario? get() = (dados as? Carga.Pronto)?.valor

private fun List<Gasto>.soma(dia: String) = filter { it.data == dia }.fold(BigDecimal.ZERO) { s, g -> s + g.valor }

/** O Calendário (calendar.spec.ts e grafico-do-mes.spec.ts) com a API falsa da demonstração, em 28/10/2026. */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarioViewModelTest {
    @get:Rule val pasta = TemporaryFolder()
    private val loja = ViewModelStore()

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun devolver() {
        loja.clear()
    }

    @Test
    fun `a grade mostra o gasto, a receita e os lembretes de cada dia`() = runTest {
        val montagem = montar(pasta.root, loja)
        val estado = montagem.calendario().estado.esperar { it.carregado != null }
        val dados = estado.carregado!!
        assertEquals("2026-10", estado.mes)
        assertEquals("BRL", estado.moeda)
        assertEquals(listOf(null, null, null, null), estado.grade.take(4))
        val dia = { n: Int -> estado.grade.filterNotNull().single { it.dia == n } }
        assertEquals(0, dia(5).receitas.compareTo(BigDecimal(10700)))
        assertEquals(0, dia(3).gastos.compareTo(dados.gastos.soma("2026-10-03")))
        assertTrue(dia(3).gastos.signum() > 0)
        assertEquals(1, dia(15).lembretes)
        assertEquals(1, dia(25).lembretes)
        assertTrue(dia(28).hoje)
        assertEquals(0, dia(29).gastos.signum())
        assertNull(estado.diaEscolhido)
        assertTrue(dados.categorias.isNotEmpty())
        assertEquals(dados.categorias.first(), estado.nova.categoria)
    }

    @Test
    fun `escolher o dia mostra a lista dele, marca no grafico e leva a nova nota para la`() = runTest {
        val calendario = montar(pasta.root, loja).calendario()
        calendario.estado.esperar { it.carregado != null }
        calendario.escolherDia("2026-10-25")
        val aberto = calendario.estado.value
        assertEquals(listOf("Revisão do carro"), aberto.lembretesDoDia.map { it.titulo })
        assertEquals("2026-10-25", aberto.nova.data)
        assertEquals(25, aberto.destaque?.dia)
        calendario.escolherDia("2026-10-05")
        assertEquals(setOf("Salário Ana", "Salário Beto"), calendario.estado.value.receitasDoDia.map { it.descricao }.toSet())
        assertTrue(calendario.estado.value.lembretesDoDia.isEmpty())
        // Tocar de novo fecha.
        calendario.escolherDia("2026-10-05")
        assertNull(calendario.estado.value.diaEscolhido)
        assertNull(calendario.estado.value.destaque)
        // Trocar de mês fecha o dia e busca o mês novo.
        calendario.escolherDia("2026-10-05")
        calendario.mudarMes("2026-11")
        val novembro = calendario.estado.esperar { it.mes == "2026-11" && it.carregado != null }
        assertNull(novembro.diaEscolhido)
        assertEquals(1, novembro.grade.filterNotNull().single { it.dia == 2 }.lembretes)
        assertTrue(novembro.semMovimento) // mês futuro: nada aconteceu ainda
    }

    @Test
    fun `o grafico acumula ate hoje, o toque mostra o dia e a tabela tem as semanas`() = runTest {
        val calendario = montar(pasta.root, loja).calendario()
        val estado = calendario.estado.esperar { it.carregado != null }
        val gastosDoMes = estado.carregado!!.gastos.fold(BigDecimal.ZERO) { s, g -> s + g.valor }
        assertEquals(28, estado.serie.size)
        assertEquals(0, estado.serie.last().receitasAteAqui.compareTo(BigDecimal(10700)))
        assertEquals(0, estado.serie.last().gastosAteAqui.compareTo(gastosDoMes))
        assertFalse(estado.semMovimento)
        assertEquals(listOf(1, 8, 15, 22, 29), estado.semanas.map { it.de })
        assertEquals(0, estado.semanas[0].receitas.compareTo(BigDecimal(10700)))
        assertEquals(0, estado.semanas.last().gastos.signum()) // 29 a 31 ainda não aconteceram
        assertEquals(0, estado.semanas.fold(BigDecimal.ZERO) { s, sem -> s + sem.gastos }.compareTo(gastosDoMes))

        calendario.estado.test {
            awaitItem()
            calendario.apontarNoGrafico(5)
            assertEquals(5, awaitItem().destaque?.dia)
            calendario.apontarNoGrafico(40) // além de hoje: fica no último dia que aconteceu
            assertEquals(28, awaitItem().destaque?.dia)
            calendario.apontarNoGrafico(null)
            assertNull(awaitItem().destaque)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nota sem titulo ou com valor e sem data nao vai para o backend`() = runTest {
        val montagem = montar(pasta.root, loja)
        val calendario = montagem.calendario()
        calendario.estado.esperar { it.carregado != null }
        calendario.criarNota()
        assertTrue(calendario.estado.value.faltouTitulo)
        assertEquals(MensagemDeErro.DoApp(R.string.calendario_falta_titulo), calendario.estado.value.erro)
        calendario.mudarTitulo("Show")
        assertFalse(calendario.estado.value.faltouTitulo)
        calendario.mudarValor(BigDecimal("120"))
        calendario.mudarComData(false)
        calendario.criarNota()
        assertEquals(MensagemDeErro.DoApp(R.string.calendario_falta_data), calendario.estado.value.erro)
        assertTrue(montagem.pedidos("POST /api/notes").isEmpty())
    }

    @Test
    fun `nota com valor vira gasto e vai com a data e a categoria`() = runTest {
        val montagem = montar(pasta.root, loja)
        val calendario = montagem.calendario()
        calendario.estado.esperar { it.carregado != null }
        calendario.escolherDia("2026-10-30")
        calendario.mudarTitulo("  Dentista ")
        calendario.mudarValor(BigDecimal("200"))
        assertTrue(calendario.estado.value.nova.viraGasto)
        calendario.mudarCategoria("Farmácia/Saúde")
        calendario.criarNota()
        val depois = calendario.estado.esperar { it.aviso != null && it.lembretesDoDia.any { n -> n.titulo == "Dentista" } }
        assertEquals(R.string.calendario_nota_criada, depois.aviso)
        assertEquals("", depois.nova.titulo)
        assertNull(depois.nova.valor)
        assertEquals("2026-10-30", depois.nova.data)
        val corpo = montagem.pedidos("POST /api/notes").single()
        assertTrue(corpo, corpo.contains(""""titulo":"Dentista""""))
        assertTrue(corpo, corpo.contains(""""data":"2026-10-30""""))
        assertTrue(corpo, corpo.contains(""""valor":200"""))
        assertTrue(corpo, corpo.contains(""""categoria":"Farmácia/Saúde""""))
        assertEquals(1, montagem.lancamentos.versao.value)
    }

    @Test
    fun `nota sem valor e sem data vai para Sem data, sem categoria`() = runTest {
        val montagem = montar(pasta.root, loja)
        val calendario = montagem.calendario()
        assertTrue(calendario.estado.esperar { it.carregado != null }.semData.isEmpty())
        calendario.mudarTitulo("Ligar para o banco")
        calendario.mudarTexto("Pedir a segunda via")
        calendario.mudarComData(false)
        assertFalse(calendario.estado.value.nova.viraGasto)
        calendario.criarNota()
        val depois = calendario.estado.esperar { it.semData.isNotEmpty() }
        assertEquals("Ligar para o banco", depois.semData.single().titulo)
        val corpo = montagem.pedidos("POST /api/notes").single()
        assertFalse(corpo, corpo.contains(""""data""""))
        assertFalse(corpo, corpo.contains(""""categoria""""))
        assertTrue(corpo, corpo.contains(""""valor":0"""))
    }

    @Test
    fun `apaga a nota so depois de confirmar e avisa as outras abas`() = runTest {
        val montagem = montar(pasta.root, loja)
        val calendario = montagem.calendario()
        calendario.estado.esperar { it.carregado != null }
        calendario.escolherDia("2026-10-25")
        val revisao = calendario.estado.value.lembretesDoDia.single()

        calendario.pedirParaApagar(revisao)
        assertEquals(revisao, calendario.estado.value.apagando)
        calendario.desistir()
        assertNull(calendario.estado.value.apagando)
        assertTrue(montagem.pedidos("DELETE").isEmpty())

        calendario.pedirParaApagar(revisao)
        calendario.confirmar()
        val depois = calendario.estado.esperar { it.carregado?.notas?.none { n -> n.id == revisao.id } == true && it.aviso != null }
        assertNull(depois.apagando)
        assertEquals(R.string.calendario_nota_apagada, depois.aviso)
        assertEquals(listOf("DELETE /api/notes/${revisao.id}"), montagem.pedidos("DELETE"))
        assertEquals(1, montagem.lancamentos.versao.value)
    }

    @Test
    fun `erro ao apagar aparece e nada muda`() = runTest {
        val montagem = montar(pasta.root, loja)
        val recusa = object : ServicoDeFinancas by montagem.rede.financas {
            override suspend fun excluirNota(id: String) = throw ErroDaApi.DoServidor(403, "PROIBIDO", "Nota de outra pessoa.", emptyList(), null)
        }
        val calendario = montagem.calendario(recusa)
        val nota = calendario.estado.esperar { it.carregado != null }.carregado!!.notas.first()
        calendario.pedirParaApagar(nota)
        calendario.confirmar()
        assertEquals(MensagemDeErro.DoServidor("Nota de outra pessoa."), calendario.estado.esperar { it.erro != null }.erro)
        assertEquals(0, montagem.lancamentos.versao.value)
    }

    @Test
    fun `recarrega quando algo e lancado em outra tela`() = runTest {
        val montagem = montar(pasta.root, loja)
        val calendario = montagem.calendario()
        calendario.estado.esperar { it.carregado != null }
        val antes = montagem.pedidos("GET /api/notes").size
        montagem.lancamentos.avisar()
        calendario.estado.esperar { montagem.pedidos("GET /api/notes").size > antes && it.carregado != null }
    }

    @Test
    fun `sem internet mostra o guardado com a hora, ou o erro quando nao ha nada`() = runTest {
        val montagem = montar(pasta.root, loja)
        montagem.calendario().estado.esperar { it.carregado != null }
        assertNotNull(montagem.guardado.respostas["calendario/2026-10"]) // guardou para depois

        val semRede = object : ServicoDeFinancas by montagem.rede.financas {
            override suspend fun listarNotas(): List<Nota> = throw ErroDaApi.SemInternet(IOException("sem rede"))
        }
        montagem.conectado.value = false
        val offline = montagem.calendario(semRede)
        val guardado = offline.estado.esperar { it.guardadoEm != null }
        assertFalse(guardado.conectado)
        assertEquals(2, guardado.carregado!!.notas.count { it.data?.startsWith("2026-10") == true })

        offline.mudarMes("2026-08")
        val vazio = offline.estado.esperar { it.mes == "2026-08" && it.dados is Carga.Falhou }
        assertTrue((vazio.dados as Carga.Falhou).erro is ErroDaApi.SemInternet)
        assertNull(vazio.guardadoEm)
    }
}
