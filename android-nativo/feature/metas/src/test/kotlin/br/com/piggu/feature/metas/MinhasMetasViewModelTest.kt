package br.com.piggu.feature.metas

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.model.MetaDoMes
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeEstiloDeVida
import br.com.piggu.core.testing.DIA_DAS_CAPTURAS
import br.com.piggu.core.testing.RedeDeTeste
import br.com.piggu.core.testing.RespostasFixas
import br.com.piggu.core.testing.esperar
import br.com.piggu.core.testing.redeDeTeste
import br.com.piggu.core.ui.FalhaAoCarregar
import br.com.piggu.core.ui.formatos.mesPorExtenso
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
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private class GuardadoNaMemoria : RespostasGuardadasDao {
    val respostas = ConcurrentHashMap<String, RespostaGuardada>()
    override suspend fun ler(chave: String) = respostas[chave]
    override suspend fun guardar(resposta: RespostaGuardada) { respostas[resposta.chave] = resposta }
    override suspend fun apagarTudo() = respostas.clear()
}

private val EstadoDasMetas.prontas: List<MetaDoMes>? get() = (metas as? Carga.Pronto)?.valor

/**
 * Minhas metas (minhas-metas.ts e minhas-metas.spec.ts), com a API falsa da demonstração: em
 * outubro de 2026 a Ana tem "Ler 2 livros" (1 de 2), a academia concluída e o delivery (1 de 4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class MinhasMetasViewModelTest {
    @get:Rule val pasta = TemporaryFolder()
    private val loja = ViewModelStore()
    private val fixas = RespostasFixas()
    private val guardado = GuardadoNaMemoria()
    private val conectado = MutableStateFlow(true)
    private lateinit var rede: RedeDeTeste
    private var criados = 0

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())
    // Sem resetMain: resposta atrasada da API falsa (outra thread) iria para um Main desfeito e derrubava o teste seguinte no CI.

    @After fun devolver() {
        loja.clear()
    }

    private suspend fun TestScope.entrar() {
        rede = redeDeTeste(pasta.root, fixas)
        rede.entrar()
    }

    private fun metas(servico: ServicoDeEstiloDeVida = rede.estiloDeVida, relogio: Clock = DIA_DAS_CAPTURAS): MinhasMetasViewModel =
        ViewModelProvider.create(
            loja,
            viewModelFactory { initializer { MinhasMetasViewModel(servico, CacheDeRespostas(guardado, relogio), relogio, conectado) } },
        )["vm${criados++}", MinhasMetasViewModel::class]

    private fun pedidos(inicio: String) = fixas.pedidos.filter { it.startsWith(inicio) }

    @Test
    fun `lista as metas do mes com o progresso e conta as concluidas no anel`() = runTest {
        entrar()
        val estado = metas().estado.esperar { it.prontas != null }
        assertEquals("2026-10", estado.mes)
        assertEquals(listOf("Ler 2 livros", "Ir à academia 12 vezes", "No máximo 4 deliveries"), estado.lista.map { it.titulo })
        assertEquals(1, estado.concluidas)
        // O anel conta metas concluídas (1 de 3), não a soma do progresso.
        assertEquals(33, estado.percentual)
        assertEquals(R.string.metas_mensagem_ritmo, estado.mensagem)
        val ler = estado.lista.first()
        assertEquals(0.5f, estado.progresso(ler))
        assertEquals(1f, estado.progresso(ler.copy(atual = 9)))
        assertNull(estado.guardadoEm)
    }

    @Test
    fun `cria, avanca ate concluir, edita e apaga so depois de confirmar`() = runTest {
        entrar()
        val metas = metas()
        metas.estado.esperar { it.prontas != null }

        metas.abrirNova()
        metas.mudarTitulo("  Beber água  ")
        metas.mudarAlvo("2")
        metas.mudarAtual("1")
        metas.salvar()
        val criada = metas.estado.esperar { e -> e.lista.any { it.titulo == "Beber água" } && !e.salvando }
        assertFalse(criada.formulario.aberto)
        assertEquals(listOf("""POST /api/personal-goals {"titulo":"Beber água","alvo":2,"atual":1}"""), pedidos("POST /api/personal-goals"))
        val agua = criada.lista.last()
        assertEquals(25, criada.percentual)

        metas.avancar(agua)
        val feita = metas.estado.esperar { e -> e.lista.any { it.id == agua.id && it.concluida } }
        assertEquals(50, feita.percentual)
        assertEquals("""PUT /api/personal-goals/${agua.id} {"titulo":"Beber água","alvo":2,"atual":2}""", pedidos("PUT").single())

        metas.editar(feita.lista.first())
        assertEquals(FormularioDeMeta(true, "meta-ler", "Ler 2 livros", "2", "1"), metas.estado.value.formulario)
        metas.mudarAtual("2")
        metas.salvar()
        val todas = metas.estado.esperar { e -> e.lista.first().concluida && !e.formulario.aberto }
        assertEquals(75, todas.percentual)

        metas.pedirParaApagar(agua)
        metas.desistir()
        assertTrue(pedidos("DELETE").isEmpty())
        metas.pedirParaApagar(agua)
        metas.confirmar()
        val depois = metas.estado.esperar { e -> e.lista.none { it.id == agua.id } }
        assertNull(depois.apagando)
        assertEquals(listOf("DELETE /api/personal-goals/${agua.id}"), pedidos("DELETE"))
    }

    @Test
    fun `o formulario pede nome, quantidade a partir de 1 e progresso inteiro`() = runTest {
        entrar()
        val metas = metas()
        metas.estado.esperar { it.prontas != null }
        metas.abrirNova()
        // A validação muda o estado na hora, sem ir à rede: lê o valor, sem contar emissões.
        metas.salvar()
        metas.estado.value.formulario.let {
            assertEquals(CampoDaMeta.Titulo, it.faltou)
            assertEquals(MensagemDeErro.DoApp(R.string.metas_falta_nome), it.erro)
        }
        metas.mudarTitulo("Correr")
        metas.mudarAlvo("0")
        metas.salvar()
        assertEquals(CampoDaMeta.Alvo, metas.estado.value.formulario.faltou)
        // Só dígitos: o sinal de menos nem entra.
        metas.mudarAlvo("-3x")
        assertEquals("3", metas.estado.value.formulario.alvo)
        assertTrue(pedidos("POST /api/personal-goals").isEmpty())
    }

    @Test
    fun `mes novo comeca vazio`() = runTest {
        entrar()
        val novembro = Clock.offset(DIA_DAS_CAPTURAS, Duration.ofDays(10))
        val estado = metas(relogio = novembro).estado.esperar { it.prontas != null }
        assertEquals("2026-11", estado.mes)
        assertTrue(estado.lista.isEmpty())
        assertEquals(0, estado.percentual)
        assertEquals(R.string.metas_mensagem_vazia, estado.mensagem)
    }

    @Test
    fun `o texto de compartilhar tem so titulo e progresso, sem dinheiro`() = runTest {
        entrar()
        val estado = metas().estado.esperar { it.prontas != null }
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        val texto = estado.textoParaCompartilhar(
            contexto.getString(R.string.metas_compartilhar_cabecalho, mesPorExtenso(estado.mes)),
            contexto.getString(R.string.metas_compartilhar_linha),
        )
        assertEquals(
            "Minhas metas de Outubro de 2026:\n" +
                "⏳ Ler 2 livros: 1 de 2\n" +
                "✅ Ir à academia 12 vezes: 12 de 12\n" +
                "⏳ No máximo 4 deliveries: 1 de 4",
            texto,
        )
        assertFalse(texto.contains("R$") || texto.contains("€"))
    }

    @Test
    fun `sem internet mostra o guardado com a hora, ou o erro quando nao ha nada`() = runTest {
        entrar()
        metas().estado.esperar { it.prontas != null }
        assertNotNull(guardado.respostas["metas-do-mes/2026-10"])

        val semRede = object : ServicoDeEstiloDeVida by rede.estiloDeVida {
            override suspend fun listarMetasDoMes(mes: String?): List<MetaDoMes> = throw ErroDaApi.SemInternet(IOException("sem rede"))
        }
        conectado.value = false
        val offline = metas(semRede).estado.esperar { it.guardadoEm != null }
        assertEquals(3, offline.lista.size)
        assertFalse(offline.conectado)

        // No mês novo não há guardado: o erro de sem internet, com tentar de novo.
        val vazio = metas(semRede, Clock.offset(DIA_DAS_CAPTURAS, Duration.ofDays(10))).estado.esperar { it.metas is Carga.Falhou }
        assertEquals(FalhaAoCarregar.SemInternet, falhaDe((vazio.metas as Carga.Falhou).erro))
    }

    @Test
    fun `erro ao salvar aparece na folha e nada muda`() = runTest {
        entrar()
        val recusa = object : ServicoDeEstiloDeVida by rede.estiloDeVida {
            override suspend fun criarMetaDoMes(meta: br.com.piggu.core.model.NovaMetaDoMes): MetaDoMes =
                throw ErroDaApi.DoServidor(400, "INVALIDO", "Quantidade grande demais.", emptyList(), null)
        }
        val metas = metas(recusa)
        metas.estado.esperar { it.prontas != null }
        metas.abrirNova()
        metas.mudarTitulo("Ler")
        metas.mudarAlvo("9999999")
        metas.salvar()
        val depois = metas.estado.esperar { it.formulario.erro != null }
        assertEquals(MensagemDeErro.DoServidor("Quantidade grande demais."), depois.formulario.erro)
        assertTrue(depois.formulario.aberto)
        assertEquals(3, depois.lista.size)
    }
}
