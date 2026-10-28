package br.com.piggu.feature.contasfixas

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.Lancamentos
import br.com.piggu.core.data.ListasParaLancar
import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.model.ContaFixa
import br.com.piggu.core.model.NovaReceita
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.core.testing.DIA_DAS_CAPTURAS
import br.com.piggu.core.testing.RedeDeTeste
import br.com.piggu.core.testing.RespostasFixas
import br.com.piggu.core.testing.esperar
import br.com.piggu.core.testing.estadosDe
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
import java.io.IOException
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

private class GuardadoNaMemoria : RespostasGuardadasDao {
    val respostas = ConcurrentHashMap<String, RespostaGuardada>()
    override suspend fun ler(chave: String) = respostas[chave]
    override suspend fun guardar(resposta: RespostaGuardada) { respostas[resposta.chave] = resposta }
    override suspend fun apagarTudo() = respostas.clear()
}

private val EstadoDasContasDoMes.lista: List<ContaFixa>? get() = (contas as? Carga.Pronto)?.valor

/**
 * Contas do mês (bills.ts, bills.spec.ts e bills-acoes.spec.ts), com a API falsa da demonstração.
 * Em 28/10/2026 a demo tem Aluguel, Netflix e Internet pagas e a Academia (dia 25) vencida.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContasDoMesViewModelTest {
    @get:Rule val pasta = TemporaryFolder()
    private val loja = ViewModelStore()
    private val fixas = RespostasFixas()
    private val guardado = GuardadoNaMemoria()
    private val lancamentos = Lancamentos()
    private val conectado = MutableStateFlow(true)
    private val cache = CacheDeRespostas(guardado, DIA_DAS_CAPTURAS)
    private lateinit var rede: RedeDeTeste
    private var criados = 0

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun devolver() {
        loja.clear()
    }

    private suspend fun TestScope.entrar() {
        rede = redeDeTeste(pasta.root, fixas)
        rede.entrar()
    }

    private fun contas(financas: ServicoDeFinancas = rede.financas): ContasDoMesViewModel =
        ViewModelProvider.create(
            loja,
            viewModelFactory {
                initializer {
                    ContasDoMesViewModel(financas, cache, ListasParaLancar(cache, financas, rede.identidade), rede.sessao, lancamentos, DIA_DAS_CAPTURAS, conectado)
                }
            },
        )["vm${criados++}", ContasDoMesViewModel::class]

    private fun pedidos(inicio: String) = fixas.pedidos.filter { it.startsWith(inicio) }

    @Test
    fun `agrupa vencidas, a vencer e pagas, soma o em aberto e troca de mes`() = runTest {
        entrar()
        val contas = contas()
        val estado = contas.estado.esperar { it.lista != null && it.categorias.isNotEmpty() }
        assertEquals("2026-10", estado.mes)
        assertEquals(listOf("Academia"), estado.vencidas.map { it.descricao })
        assertTrue(estado.aVencer.isEmpty())
        assertEquals(listOf("Aluguel", "Netflix", "Internet"), estado.pagas.map { it.descricao })
        assertEquals(0, estado.totalEmAberto.compareTo(BigDecimal("99")))
        assertEquals(estado.categorias.first(), estado.formulario.categoria)
        assertNull(estado.guardadoEm)

        contas.mudarMes("2026-09")
        val setembro = contas.estado.esperar { it.mes == "2026-09" && it.lista?.all { c -> c.vencimento.startsWith("2026-09") } == true }
        assertEquals(4, setembro.pagas.size)
        assertEquals(0, setembro.totalEmAberto.signum())
    }

    @Test
    fun `marcar como paga lanca o gasto do mes da tela e avisa as outras telas`() = runTest {
        entrar()
        val meses = mutableListOf<String>()
        val contas = contas(
            object : ServicoDeFinancas by rede.financas {
                override suspend fun pagarContaFixa(id: String, mes: String, vazio: kotlinx.serialization.json.JsonObject) =
                    rede.financas.pagarContaFixa(id, mes, vazio).also { meses += mes }
            },
        )
        val academia = contas.estado.esperar { it.vencidas.isNotEmpty() }.vencidas.single()

        contas.pagar(academia)
        val depois = contas.estado.esperar { it.vencidas.isEmpty() && it.lista != null }
        assertEquals(R.string.contas_paga_aviso, depois.aviso)
        assertFalse(depois.ocupado)
        assertTrue(depois.pagas.any { it.id == academia.id })
        assertEquals(listOf("POST /api/bills/${academia.id}/pay {}"), pedidos("POST /api/bills/"))
        assertEquals(listOf("2026-10"), meses)
        assertEquals(1, lancamentos.versao.value)

        // Já paga no mês: o erro do servidor aparece e nada é lançado de novo.
        contas.pagar(academia)
        val recusada = contas.estado.esperar { it.erro != null }
        assertEquals(MensagemDeErro.DoServidor("Essa conta já foi paga neste mês."), recusada.erro)
        assertNull(recusada.aviso)
        assertEquals(1, lancamentos.versao.value)
    }

    @Test
    fun `nova conta pede descricao, valor e dia validos e manda o automatico`() = runTest {
        entrar()
        val contas = contas()
        contas.estado.esperar { it.lista != null && it.categorias.isNotEmpty() }
        contas.abrirFormulario()
        assertTrue(contas.estado.value.formulario.aberto)

        val invalida = MensagemDeErro.DoApp(R.string.contas_nova_invalida)
        contas.criar()
        assertEquals(invalida, contas.estado.value.formulario.erro)

        contas.mudarDescricao(" Condomínio ")
        contas.mudarValor(BigDecimal("450"))
        contas.mudarDia("40")
        contas.criar()
        assertEquals(invalida, contas.estado.value.formulario.erro)
        contas.mudarValor(BigDecimal.ZERO)
        contas.mudarDia("8")
        contas.criar()
        assertEquals(invalida, contas.estado.value.formulario.erro)
        assertTrue(pedidos("POST /api/bills").isEmpty())

        contas.mudarDia("8a9") // só dígitos, até dois
        assertEquals("89", contas.estado.value.formulario.dia)
        contas.mudarDia("8")
        contas.mudarValor(BigDecimal("450"))
        contas.mudarCategoria("Assinaturas")
        contas.mudarAutomatico(true)
        contas.criar()
        val criada = contas.estado.esperar { it.lista?.any { c -> c.descricao == "Condomínio" } == true && !it.ocupado }
        assertEquals(
            listOf("""POST /api/bills {"descricao":"Condomínio","categoria":"Assinaturas","valor":450,"dia":8,"automatico":true}"""),
            pedidos("POST /api/bills"),
        )
        assertEquals(R.string.contas_criada_aviso, criada.aviso)
        assertFalse(criada.formulario.aberto)
        assertEquals("", criada.formulario.descricao)
        assertNull(criada.formulario.valor)
        assertTrue(criada.formulario.automatico) // fica para a próxima, como no site
        assertTrue(criada.lista!!.first { it.descricao == "Condomínio" }.automatico)
    }

    @Test
    fun `erro ao criar aparece no formulario e libera o botao`() = runTest {
        entrar()
        val recusa = object : ServicoDeFinancas by rede.financas {
            override suspend fun criarContaFixa(conta: br.com.piggu.core.model.NovaContaFixa) =
                throw ErroDaApi.DoServidor(400, "VALIDACAO", "Limite de contas.", emptyList(), null)
        }
        val contas = contas(recusa)
        contas.estado.esperar { it.lista != null }
        contas.abrirFormulario()
        contas.mudarDescricao("Luz")
        contas.mudarValor(BigDecimal("100"))
        val estados = estadosDe(contas.estado)
        contas.criar()
        contas.estado.esperar { !it.ocupado && it.formulario.erro != null }.let {
            assertTrue(it.formulario.aberto)
            assertEquals(MensagemDeErro.DoServidor("Limite de contas."), it.formulario.erro)
        }
        assertTrue(estados.any { it.ocupado })
        assertEquals(0, lancamentos.versao.value)
    }

    @Test
    fun `apaga so depois de confirmar`() = runTest {
        entrar()
        val contas = contas()
        val netflix = contas.estado.esperar { it.lista != null }.pagas.first { it.descricao == "Netflix" }

        contas.pedirParaApagar(netflix)
        assertEquals(Confirmacao.ApagarConta(netflix), contas.estado.value.confirmacao)
        contas.desistir()
        assertNull(contas.estado.value.confirmacao)
        assertTrue(pedidos("DELETE").isEmpty())

        contas.pedirParaApagar(netflix)
        contas.confirmar()
        val depois = contas.estado.esperar { it.lista?.none { c -> c.id == netflix.id } == true }
        assertNull(depois.confirmacao)
        assertEquals(R.string.contas_apagada_aviso, depois.aviso)
        assertEquals(listOf("DELETE /api/bills/${netflix.id}"), pedidos("DELETE"))
    }

    @Test
    fun `mostra as receitas fixas e para de repetir`() = runTest {
        entrar()
        rede.financas.lancarReceita(
            NovaReceita(
                data = "2026-10-20", descricao = "Salário", categoria = "Salário", valor = BigDecimal("2000"),
                compartilhado = false, recorrente = true, segundoDia = 5, segundoValor = BigDecimal("3000"),
            ),
        )
        val contas = contas()
        // As receitas fixas e as contas chegam em cargas separadas: espera as duas.
        val fixa = contas.estado.esperar { it.receitasFixas.isNotEmpty() && it.lista != null }.receitasFixas.single()
        assertEquals("Salário", fixa.descricao)
        assertEquals(5, fixa.segundoDia)
        // A receita fixa não entra nas contas a pagar.
        assertTrue(contas.estado.value.lista!!.none { it.id == fixa.id })

        contas.pedirParaPararDeRepetir(fixa)
        assertEquals(Confirmacao.PararDeRepetir(fixa), contas.estado.value.confirmacao)
        contas.confirmar()
        val depois = contas.estado.esperar { it.receitasFixas.isEmpty() }
        assertEquals(R.string.contas_parou_aviso, depois.aviso)
        assertEquals(listOf("DELETE /api/bills/${fixa.id}"), pedidos("DELETE"))
    }

    @Test
    fun `sem internet mostra o guardado com a hora, ou o erro quando nao ha nada`() = runTest {
        entrar()
        contas().estado.esperar { it.lista != null }
        guardado.respostas.getValue("contas-do-mes/2026-10")

        val semRede = object : ServicoDeFinancas by rede.financas {
            override suspend fun contasFixas(mes: String): List<ContaFixa> = throw ErroDaApi.SemInternet(IOException("sem rede"))
        }
        conectado.value = false
        val offline = contas(semRede)
        val comGuardado = offline.estado.esperar { it.guardadoEm != null }
        assertEquals(4, comGuardado.lista!!.size)
        assertFalse(comGuardado.conectado)
        assertNotNull(comGuardado.guardadoEm)

        offline.mudarMes("2026-07")
        val vazio = offline.estado.esperar { it.mes == "2026-07" && it.contas is Carga.Falhou }
        assertEquals(FalhaAoCarregar.SemInternet, falhaDe((vazio.contas as Carga.Falhou).erro))
        assertNull(vazio.guardadoEm)
    }

    @Test
    fun `erro do servidor na lista pede tentar de novo`() = runTest {
        entrar()
        var falhar = true
        val instavel = object : ServicoDeFinancas by rede.financas {
            override suspend fun contasFixas(mes: String): List<ContaFixa> =
                if (falhar) throw ErroDaApi.DoServidor(503, null, "Fora do ar.", emptyList(), null) else rede.financas.contasFixas(mes)
        }
        val contas = contas(instavel)
        val falhou = contas.estado.esperar { it.contas is Carga.Falhou }
        assertEquals(FalhaAoCarregar.DoServidor, falhaDe((falhou.contas as Carga.Falhou).erro))
        falhar = false
        contas.tentarDeNovo()
        assertEquals(4, contas.estado.esperar { it.lista != null }.lista!!.size)
    }
}
