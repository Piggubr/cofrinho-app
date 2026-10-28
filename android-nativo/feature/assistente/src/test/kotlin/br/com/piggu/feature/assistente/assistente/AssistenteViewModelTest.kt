package br.com.piggu.feature.assistente.assistente

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import br.com.piggu.core.data.VERSAO_DOS_TERMOS
import br.com.piggu.core.datastore.ConversaGuardada
import br.com.piggu.core.model.PigguRole
import br.com.piggu.core.model.Plano
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.testing.DIA_DAS_CAPTURAS
import br.com.piggu.core.testing.RedeDeTeste
import br.com.piggu.core.testing.RespostasFixas
import br.com.piggu.core.testing.esperar
import br.com.piggu.core.testing.redeDeTeste
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Duration

/** assistente.spec.ts e conversa-do-assistente.spec.ts, com a API falsa da demonstração. */
@OptIn(ExperimentalCoroutinesApi::class)
class AssistenteViewModelTest {
    @get:Rule val pasta = TemporaryFolder()

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())
    // Sem resetMain: resposta atrasada da API falsa (outra thread) iria para um Main desfeito e derrubava o teste seguinte no CI.
    private lateinit var rede: RedeDeTeste
    private lateinit var guardada: ConversaGuardada

    private suspend fun TestScope.entrar(fixas: RespostasFixas = RespostasFixas()) {
        rede = redeDeTeste(pasta.root, fixas).also { it.entrar() }
        guardada = ConversaGuardada(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { pasta.root.resolve("a.preferences_pb") }))
    }

    private fun assistente(relogio: Clock = DIA_DAS_CAPTURAS) =
        AssistenteViewModel(rede.sessao, rede.financas, ConversaDoAssistente(guardada, relogio))

    /** Abre e espera a situação do assistente. */
    private suspend fun AssistenteViewModel.aberto(): EstadoDoAssistente {
        abrir()
        return estado.esperar { it.situacao != null }
    }

    /** A conversa vai para o aparelho depois da tela: espera o que ficou guardado. */
    private suspend fun esperarGuardada(condicao: (String?) -> Boolean) =
        withContext(Dispatchers.Default) { withTimeout(30_000) { while (!condicao(guardada.ler())) delay(10) } }

    private suspend fun AssistenteViewModel.perguntarEEsperar(pergunta: String): EstadoDoAssistente {
        perguntar(pergunta)
        return estado.esperar { !it.pensando }
    }

    @Test
    fun `sem Premium mostra o convite e nem chama o servidor`() = runTest {
        val fixas = RespostasFixas()
        entrar(fixas)
        rede.sessao.entrou(rede.sessao.usuario!!.copy(plano = Plano.GRATUITO, role = PigguRole.TITULAR))
        val assistente = assistente()
        assistente.abrir()
        assistente.estado.esperar { !it.premium && !it.carregando }
        assertNull(assistente.estado.value.situacao)
        assertTrue(fixas.pedidos.none { it.contains("/api/assistant") })
    }

    @Test
    fun `primeiro uso grava a autorizacao e a pergunta seguinte nao a repete`() = runTest {
        val fixas = RespostasFixas()
        entrar(fixas)
        val assistente = assistente()
        assertTrue(assistente.aberto().precisaAutorizar)

        assistente.autorizar()
        assertFalse(assistente.estado.esperar { it.situacao?.autorizado == true }.precisaAutorizar)
        assertTrue(fixas.pedidos.contains("""POST /api/assistant/consent {"versaoDoAviso":"$VERSAO_DOS_TERMOS"}"""))

        val respondido = assistente.perguntarEEsperar("Quanto gastei este mês?")
        assertEquals(listOf(true, false), respondido.falas.map { it.pessoa })
        assertEquals(149, respondido.situacao!!.restantes)
        assertTrue(fixas.pedidos.last().startsWith("""POST /api/assistant/ask {"pergunta":"Quanto gastei este mês?","historico":[],"moeda":"BRL"}"""))
    }

    @Test
    fun `se gravar a autorizacao falha, ela vai junto com a primeira pergunta`() = runTest {
        val fixas = RespostasFixas("POST /api/assistant/consent" to RespostasFixas.erro(503, "NEGOCIO", "fora"))
        entrar(fixas)
        val assistente = assistente()
        assistente.aberto()
        assistente.autorizar()
        assertFalse(assistente.estado.value.precisaAutorizar)

        val respondido = assistente.perguntarEEsperar("Quanto gastei?")
        assertEquals(2, respondido.falas.size)
        val pedido = fixas.pedidos.last { it.startsWith("POST /api/assistant/ask") }
        assertTrue(pedido, pedido.contains(""""autorizoIa":true,"versaoDoAviso":"$VERSAO_DOS_TERMOS""""))
    }

    @Test
    fun `sem a autorizacao o servidor pede e a pergunta volta ao campo`() = runTest {
        entrar()
        val assistente = assistente()
        assistente.aberto()
        val estado = assistente.perguntarEEsperar("Quanto gastei?")
        assertTrue(estado.precisaAutorizar)
        assertEquals("Quanto gastei?", estado.texto)
        assertEquals(emptyList<FalaGuardada>(), estado.falas)
        assertNull(estado.erro)
    }

    @Test
    fun `a segunda pergunta leva o historico e sugere a tela`() = runTest {
        val fixas = RespostasFixas()
        entrar(fixas)
        val assistente = assistente()
        assistente.aberto()
        assistente.autorizar()
        assistente.estado.esperar { it.situacao?.autorizado == true }
        val primeira = assistente.perguntarEEsperar("Quanto falta para meus objetivos?").falas.last()
        assertFalse(primeira.pessoa)
        assistente.perguntarEEsperar("E no mês passado?")

        val pedido = fixas.pedidos.last()
        val historico = """"historico":[{"pessoa":true,"texto":"Quanto falta para meus objetivos?"},{"pessoa":false,"texto":"""
        assertTrue(pedido, pedido.contains(historico))
        assertFalse(pedido.contains("autorizoIa"))
        assertEquals(4, assistente.estado.value.falas.size)
    }

    @Test
    fun `o limite do mes mostra a mensagem do servidor e devolve a pergunta`() = runTest {
        val fixas = RespostasFixas("POST /api/assistant/ask" to RespostasFixas.erro(429, "NEGOCIO", "Você já fez as 150 perguntas deste mês."))
        entrar(fixas)
        val assistente = assistente()
        assistente.aberto()
        assistente.mudarTexto("a".repeat(600))
        assertEquals(PERGUNTA_MAXIMA, assistente.estado.value.texto.length)

        val estado = assistente.perguntarEEsperar("Quanto gastei?")
        assertEquals(MensagemDeErro.DoServidor("Você já fez as 150 perguntas deste mês."), estado.erro)
        assertEquals("Quanto gastei?", estado.texto)
        assertEquals(emptyList<FalaGuardada>(), estado.falas)
        esperarGuardada { it == null }
    }

    @Test
    fun `a conversa volta ao abrir de novo, so para a mesma pessoa, e vence em 30 dias`() = runTest {
        entrar()
        val primeiro = assistente()
        primeiro.aberto()
        primeiro.autorizar()
        primeiro.estado.esperar { it.situacao?.autorizado == true }
        primeiro.perguntarEEsperar("Quanto gastei?")
        esperarGuardada { it.orEmpty().contains(""""pessoa":false""") }

        val segundo = assistente()
        segundo.abrir()
        assertEquals("Quanto gastei?", segundo.estado.esperar { it.falas.isNotEmpty() }.falas.first().texto)

        // Outra pessoa no mesmo aparelho não vê.
        rede.sessao.entrou(rede.sessao.usuario!!.copy(id = "u-outra"))
        segundo.abrir()
        segundo.estado.esperar { it.falas.isEmpty() }

        // 31 dias depois, a conversa sai da tela e do aparelho.
        val depois = assistente(Clock.offset(DIA_DAS_CAPTURAS, Duration.ofDays(31)))
        depois.abrir()
        depois.estado.esperar { it.situacao != null }
        assertEquals(emptyList<FalaGuardada>(), depois.estado.value.falas)
        assertNull(guardada.ler())
    }

    @Test
    fun `manda para a IA so as ultimas 10 falas e guarda no maximo 200`() = runTest {
        val fixas = RespostasFixas()
        entrar(fixas)
        val conversa = ConversaDoAssistente(guardada, DIA_DAS_CAPTURAS)
        val ana = rede.sessao.usuario!!.id
        conversa.guardar(ana, List(250) { FalaGuardada(it % 2 == 0, "fala $it", em = conversa.agora()) })
        assertEquals("fala 50", conversa.ler(ana).first().texto)

        val assistente = assistente()
        assistente.aberto()
        assistente.autorizar()
        assistente.estado.esperar { it.situacao?.autorizado == true }
        assistente.perguntarEEsperar("E agora?")
        val pedido = fixas.pedidos.last()
        assertTrue(pedido, pedido.contains(""""historico":[{"pessoa":true,"texto":"fala 240"}"""))
        assertFalse(pedido.contains("fala 239"))
    }

    @Test
    fun `esquecerConversa e Nova conversa apagam do aparelho`() = runTest {
        entrar()
        val assistente = assistente()
        assistente.aberto()
        assistente.autorizar()
        assistente.estado.esperar { it.situacao?.autorizado == true }
        assistente.perguntarEEsperar("Quanto gastei?")
        esperarGuardada { it.orEmpty().contains(""""pessoa":false""") }

        // Ao sair da conta, o app chama ConversaGuardada.esquecerConversa().
        guardada.esquecerConversa()
        val outro = assistente()
        outro.aberto()
        assertEquals(emptyList<FalaGuardada>(), outro.estado.value.falas)

        assistente.perguntarEEsperar("De novo?")
        assistente.recomecar()
        assertEquals(emptyList<FalaGuardada>(), assistente.estado.value.falas)
        esperarGuardada { it == null }
    }

    @Test
    fun `conversa corrompida nao quebra a tela`() = runTest {
        entrar()
        guardada.guardar("{nao e json")
        val assistente = assistente()
        assistente.aberto()
        assertEquals(emptyList<FalaGuardada>(), assistente.estado.value.falas)
        assertNull(guardada.ler())
    }
}
