package br.com.piggu.feature.premios

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
import br.com.piggu.core.model.PedidoDeAjuste
import br.com.piggu.core.model.Premio
import br.com.piggu.core.model.SaldoDeMoedas
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeRecompensas
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
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

private class GuardadoNaMemoria : RespostasGuardadasDao {
    val respostas = ConcurrentHashMap<String, RespostaGuardada>()
    override suspend fun ler(chave: String) = respostas[chave]
    override suspend fun guardar(resposta: RespostaGuardada) { respostas[resposta.chave] = resposta }
    override suspend fun apagarTudo() = respostas.clear()
}

/** O login respondido com outra pessoa da família (a demo sempre entra com a Ana, titular). */
internal fun entrarComo(role: String) = RespostasFixas(
    "POST /api/auth/entrar" to RespostaFixa(
        200,
        """{"accessToken":"a1","expiresIn":1800,"refreshToken":"r1","usuario":{"id":"u-beto","email":"beto@demo.piggu.app",""" +
            """"nome":"Beto Souza","primeiroNome":"Beto","role":"$role","ativo":true,"plano":"PREMIUM","familia":"familia-demo"}}""",
    ),
)

private val EstadoDosPremios.premios: List<Premio>? get() = dados?.premios

/**
 * Prêmios (rewards.ts e rewards.spec.ts), com a API falsa da demonstração: a Ana tem 120
 * Fofocoins, há Cinema (80), Jantar (150) e Dia sem louça (40), e o Beto resgatou um dia sem louça.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PremiosViewModelTest {
    @get:Rule val pasta = TemporaryFolder()
    private val loja = ViewModelStore()
    private val guardado = GuardadoNaMemoria()
    private val lancamentos = Lancamentos()
    private val conectado = MutableStateFlow(true)
    private val cache = CacheDeRespostas(guardado, DIA_DAS_CAPTURAS)
    private lateinit var fixas: RespostasFixas
    private lateinit var rede: RedeDeTeste
    private var criados = 0

    /** O "todos" de cada GET /api/prizes (as RespostasFixas guardam o caminho sem a consulta). */
    private val todos = CopyOnWriteArrayList<Boolean>()

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

    private fun contando(servico: ServicoDeRecompensas) = object : ServicoDeRecompensas by servico {
        override suspend fun listarPremios(todos: Boolean) = servico.listarPremios(todos).also { this@PremiosViewModelTest.todos += todos }
    }

    private fun premios(recompensas: ServicoDeRecompensas = rede.recompensas): PremiosViewModel =
        ViewModelProvider.create(
            loja,
            viewModelFactory {
                initializer {
                    PremiosViewModel(
                        contando(recompensas), cache, ListasParaLancar(cache, rede.financas, rede.identidade), rede.sessao,
                        lancamentos, DIA_DAS_CAPTURAS, conectado,
                    )
                }
            },
        )["vm${criados++}", PremiosViewModel::class]

    private fun pedidos(inicio: String) = fixas.pedidos.filter { it.startsWith(inicio) }

    @Test
    fun `mostra o saldo, os premios, o historico e os resgates com o nome de quem resgatou`() = runTest {
        entrar()
        val premios = premios()
        val estado = premios.estado.esperar { it.dados != null && it.membros.isNotEmpty() }
        assertTrue(estado.cuidaDosPremios)
        assertEquals(120, estado.saldo)
        assertEquals(listOf("Cinema com pipoca", "Jantar fora", "Dia sem louça"), estado.premios!!.map { it.nome })
        assertTrue(estado.podeResgatar(estado.premios!![0]))
        assertFalse(estado.podeResgatar(estado.premios!![1])) // 150 > 120: o botão fica desligado
        assertFalse(estado.podeResgatar(estado.premios!![2].copy(ativo = false)))
        val resgate = estado.dados!!.resgates.single()
        assertEquals(Aviso(R.string.premios_pessoa, listOf("Beto Souza")), estado.quemResgatou(resgate))
        assertEquals(Aviso(R.string.premios_ex_membro), estado.quemResgatou(resgate.copy(usuario = "u-antigo")))
        assertEquals(Aviso(R.string.premios_piggu), estado.quemResgatou(resgate.copy(usuario = SISTEMA)))
        assertEquals(3, estado.dados!!.saldo.historico.size)
        assertNull(estado.guardadoEm)
        // Quem cuida dos prêmios vê também os desativados.
        assertEquals(listOf(true), todos.distinct())

        // alternarHistorico muda o estado na hora: lê o valor, sem contar emissões.
        assertFalse(premios.estado.value.mostrarHistorico)
        premios.alternarHistorico()
        assertTrue(premios.estado.value.mostrarHistorico)
        premios.alternarHistorico()
        assertFalse(premios.estado.value.mostrarHistorico)
    }

    @Test
    fun `resgatar pede confirmacao, avisa o saldo novo e recarrega`() = runTest {
        entrar()
        val premios = premios()
        val cinema = premios.estado.esperar { it.dados != null }.premios!!.first { it.nome == "Cinema com pipoca" }

        premios.pedirParaResgatar(cinema)
        assertEquals(Confirmacao.Resgatar(cinema), premios.estado.value.confirmacao)
        premios.desistir()
        assertNull(premios.estado.value.confirmacao)
        assertTrue(pedidos("POST /api/prizes/").isEmpty())

        premios.pedirParaResgatar(cinema)
        premios.confirmar()
        val depois = premios.estado.esperar { it.saldo == 40 && !it.ocupado }
        assertEquals(Aviso(R.string.premios_resgatado, listOf(40)), depois.aviso)
        assertNull(depois.erro)
        assertEquals(listOf("POST /api/prizes/${cinema.id}/redemptions {}"), pedidos("POST /api/prizes/"))
        assertEquals("Cinema com pipoca", depois.dados!!.resgates.first().premio)
    }

    @Test
    fun `saldo insuficiente mostra o erro do servidor e traz o saldo de verdade`() = runTest {
        entrar()
        val premios = premios()
        val cinema = premios.estado.esperar { it.dados != null }.premios!!.first { it.preco == 80 }
        // O saldo mudou noutro aparelho: a tela ainda mostra 120.
        rede.recompensas.ajustarMoedas(PedidoDeAjuste(-100, "Noutro aparelho"))
        assertTrue(premios.estado.value.podeResgatar(cinema))

        premios.pedirParaResgatar(cinema)
        premios.confirmar()
        val recusado = premios.estado.esperar { it.erro != null && it.saldo == 20 }
        assertEquals(MensagemDeErro.DoServidor("Moedas insuficientes para esse prêmio."), recusado.erro)
        assertNull(recusado.aviso)
        assertFalse(recusado.podeResgatar(cinema))
    }

    @Test
    fun `quem cuida ajusta o saldo so com quantidade e motivo`() = runTest {
        entrar()
        val premios = premios()
        premios.estado.esperar { it.dados != null }

        premios.ajustar()
        assertEquals(MensagemDeErro.DoApp(R.string.premios_quantidade_invalida), premios.estado.value.erro)
        premios.mudarAjusteValor("2a0")
        assertEquals("20", premios.estado.value.ajusteValor)
        premios.ajustar()
        assertEquals(MensagemDeErro.DoApp(R.string.premios_motivo_vazio), premios.estado.value.erro)
        premios.mudarAjusteMotivo("   ")
        premios.ajustar()
        assertEquals(MensagemDeErro.DoApp(R.string.premios_motivo_vazio), premios.estado.value.erro)
        assertTrue(pedidos("POST /api/coins").isEmpty())

        premios.mudarAjusteValor("-15")
        assertEquals("-15", premios.estado.value.ajusteValor)
        premios.mudarAjusteMotivo(" Esqueceu a louça ")
        premios.ajustar()
        val depois = premios.estado.esperar { it.saldo == 105 && !it.ocupado }
        assertEquals(listOf("""POST /api/coins/adjustments {"valor":-15,"motivo":"Esqueceu a louça"}"""), pedidos("POST /api/coins"))
        assertEquals(Aviso(R.string.premios_saldo_atualizado), depois.aviso)
        assertNull(depois.erro)
        assertEquals("", depois.ajusteValor)
        assertEquals("", depois.ajusteMotivo)
    }

    @Test
    fun `quem cuida cria, edita e exclui premios`() = runTest {
        entrar()
        val premios = premios()
        premios.estado.esperar { it.dados != null }

        premios.abrirNovoPremio()
        premios.salvarPremio()
        assertEquals(MensagemDeErro.DoApp(R.string.premios_nome_vazio), premios.estado.value.formulario.erro)
        premios.mudarNome(" Massagem ")
        premios.salvarPremio()
        assertEquals(MensagemDeErro.DoApp(R.string.premios_preco_invalido), premios.estado.value.formulario.erro)
        premios.mudarPreco("0")
        premios.salvarPremio()
        assertEquals(MensagemDeErro.DoApp(R.string.premios_preco_invalido), premios.estado.value.formulario.erro)
        premios.mudarPreco("8x0")
        premios.salvarPremio()
        val criado = premios.estado.esperar { it.premios?.any { p -> p.nome == "Massagem" } == true && !it.ocupado }
        assertEquals(listOf("""POST /api/prizes {"nome":"Massagem","descricao":"","preco":80,"ativo":true}"""), pedidos("POST /api/prizes"))
        assertFalse(criado.formulario.aberto)

        val folga = criado.premios!!.first { it.nome == "Dia sem louça" }
        premios.editarPremio(folga)
        assertEquals(FormularioDePremio(true, folga, "Dia sem louça", "O outro lava tudo", "40"), premios.estado.value.formulario)
        premios.mudarPreco("55")
        premios.salvarPremio()
        val editado = premios.estado.esperar { it.premios?.any { p -> p.id == folga.id && p.preco == 55 } == true && !it.ocupado }
        assertEquals(listOf("""PUT /api/prizes/${folga.id} {"nome":"Dia sem louça","descricao":"O outro lava tudo","preco":55,"ativo":true}"""), pedidos("PUT"))
        assertFalse(editado.formulario.aberto)

        premios.pedirParaRemover(folga)
        assertEquals(Confirmacao.Remover(folga), premios.estado.value.confirmacao)
        premios.confirmar()
        premios.estado.esperar { it.premios?.none { p -> p.id == folga.id } == true }
        assertEquals(listOf("DELETE /api/prizes/${folga.id}"), pedidos("DELETE"))
    }

    @Test
    fun `erro ao salvar o premio fica no formulario`() = runTest {
        entrar()
        val recusa = object : ServicoDeRecompensas by rede.recompensas {
            override suspend fun criarPremio(premio: br.com.piggu.core.model.NovoPremio) =
                throw ErroDaApi.DoServidor(403, "PROIBIDO", "Só quem cuida dos prêmios.", emptyList(), null)
        }
        val premios = premios(recusa)
        premios.estado.esperar { it.dados != null }
        premios.abrirNovoPremio()
        premios.mudarNome("Pizza")
        premios.mudarPreco("50")
        premios.salvarPremio()
        val falhou = premios.estado.esperar { it.formulario.erro != null && !it.ocupado }
        assertTrue(falhou.formulario.aberto)
        assertEquals(MensagemDeErro.DoServidor("Só quem cuida dos prêmios."), falhou.formulario.erro)
        assertNull(falhou.erro)
    }

    @Test
    fun `o membro so ve os premios ativos e nao cuida deles`() = runTest {
        entrar(entrarComo("MEMBRO"))
        val premios = premios()
        val estado = premios.estado.esperar { it.dados != null }
        assertFalse(estado.cuidaDosPremios)
        assertEquals(listOf(false), todos.distinct())
        // Resgatar continua de todos.
        assertTrue(estado.podeResgatar(estado.premios!!.first { it.preco == 80 }))
    }

    @Test
    fun `o parceiro cuida dos premios`() = runTest {
        entrar(entrarComo("PARCEIRO"))
        assertTrue(premios().estado.esperar { it.dados != null }.cuidaDosPremios)
    }

    @Test
    fun `recarrega quando algo e lancado`() = runTest {
        entrar()
        var consultas = 0
        val premios = premios(
            object : ServicoDeRecompensas by rede.recompensas {
                override suspend fun consultarSaldo(): SaldoDeMoedas = rede.recompensas.consultarSaldo().also { consultas++ }
            },
        )
        premios.estado.esperar { it.dados != null }
        assertEquals(1, consultas)
        rede.recompensas.ajustarMoedas(PedidoDeAjuste(5, "Noutro aparelho"))
        lancamentos.avisar()
        premios.estado.esperar { it.saldo == 125 }
        assertEquals(2, consultas)
    }

    @Test
    fun `sem internet mostra o guardado com a hora, ou o erro quando nao ha nada`() = runTest {
        entrar()
        premios().estado.esperar { it.dados != null }
        assertNotNull(guardado.respostas["premios"])

        val semRede = object : ServicoDeRecompensas by rede.recompensas {
            override suspend fun consultarSaldo(): SaldoDeMoedas = throw ErroDaApi.SemInternet(IOException("sem rede"))
        }
        conectado.value = false
        val comGuardado = premios(semRede).estado.esperar { it.guardadoEm != null }
        assertEquals(120, comGuardado.saldo)
        assertEquals(3, comGuardado.premios!!.size)
        assertFalse(comGuardado.conectado)

        guardado.respostas.remove("premios")
        val semNada = premios(semRede).estado.esperar { it.carga is Carga.Falhou }
        assertEquals(FalhaAoCarregar.SemInternet, falhaDe((semNada.carga as Carga.Falhou).erro))
        assertNull(semNada.guardadoEm)
    }

    @Test
    fun `erro do servidor pede tentar de novo`() = runTest {
        entrar()
        var falhar = true
        val instavel = object : ServicoDeRecompensas by rede.recompensas {
            override suspend fun listarResgates() =
                if (falhar) throw ErroDaApi.DoServidor(503, null, "Fora do ar.", emptyList(), null) else rede.recompensas.listarResgates()
        }
        val premios = premios(instavel)
        val falhou = premios.estado.esperar { it.carga is Carga.Falhou }
        assertEquals(FalhaAoCarregar.DoServidor, falhaDe((falhou.carga as Carga.Falhou).erro))
        falhar = false
        premios.tentarDeNovo()
        assertEquals(120, premios.estado.esperar { it.dados != null }.saldo)
    }
}
