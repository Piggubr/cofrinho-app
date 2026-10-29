package br.com.piggu.feature.familia

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.EstadoDaSessao
import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.model.AcaoDeAuditoria
import br.com.piggu.core.model.Familia
import br.com.piggu.core.model.PigguRole
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeIdentidade
import br.com.piggu.core.testing.DIA_DAS_CAPTURAS
import br.com.piggu.core.testing.RedeDeTeste
import br.com.piggu.core.testing.RespostaFixa
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
import java.util.concurrent.ConcurrentHashMap

private class GuardadoNaMemoria : RespostasGuardadasDao {
    val respostas = ConcurrentHashMap<String, RespostaGuardada>()
    override suspend fun ler(chave: String) = respostas[chave]
    override suspend fun guardar(resposta: RespostaGuardada) { respostas[resposta.chave] = resposta }
    override suspend fun apagarTudo() = respostas.clear()
}

/** Entrar como outra pessoa da família da demo (Beto é o membro). */
private fun entrarComo(id: String, nome: String, role: String) = RespostaFixa(
    200,
    """{"accessToken":"a1","expiresIn":1800,"refreshToken":"r1","usuario":{"id":"$id","email":"x@demo.piggu.app","nome":"$nome",""" +
        """"primeiroNome":"$nome","role":"$role","ativo":true,"plano":"PREMIUM","familia":"familia-demo"}}""",
)

/** A família da demo (Ana titular, Beto membro), com o Beto como o teste quiser. */
private fun familia(betoPapel: String = "MEMBRO", pendente: Boolean = false, planoCasal: Boolean = false) = RespostaFixa(
    200,
    """{"id":"familia-demo","nome":"Família Demo","plano":"PREMIUM","planoFamilia":false,"planoCasal":$planoCasal,"convites":[],"membros":[""" +
        """{"id":"u-ana","nome":"Ana Souza","email":"ana@demo.piggu.app","papel":"TITULAR","premium":true},""" +
        """{"id":"u-beto","nome":"Beto Souza","email":"beto@demo.piggu.app","papel":"$betoPapel","premium":true,"parceriaPendente":$pendente}]}""",
)

private val EstadoDaFamilia.casa: Familia? get() = (familia as? Carga.Pronto)?.valor

/**
 * Família (family.spec.ts e family-acoes.spec.ts), com a sessão e a rede de verdade, respondidas
 * pela API da demonstração (Ana titular, Beto membro, o convite da vovó). O que a demo não faz
 * (remover, mudar papel, parceria, sair) vem das [RespostasFixas].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FamiliaViewModelTest {
    @get:Rule val pasta = TemporaryFolder()
    private val loja = ViewModelStore()
    private val guardado = GuardadoNaMemoria()
    private val conectado = MutableStateFlow(true)
    private val cache = CacheDeRespostas(guardado, DIA_DAS_CAPTURAS)
    private lateinit var rede: RedeDeTeste
    private lateinit var fixas: RespostasFixas
    private var criados = 0

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())
    // Sem resetMain: resposta atrasada da API falsa (outra thread) iria para um Main desfeito e derrubava o teste seguinte no CI.

    @After fun devolver() {
        loja.clear()
    }

    private suspend fun TestScope.entrar(vararg respostas: Pair<String, RespostaFixa>) {
        fixas = RespostasFixas(*respostas)
        rede = redeDeTeste(pasta.root, fixas)
        rede.entrar()
    }

    private fun familiaVm(identidade: ServicoDeIdentidade = rede.identidade): FamiliaViewModel =
        ViewModelProvider.create(
            loja,
            viewModelFactory { initializer { FamiliaViewModel(identidade, rede.financas, cache, rede.sessao, DIA_DAS_CAPTURAS, conectado) } },
        )["vm${criados++}", FamiliaViewModel::class]

    private fun pedidos(inicio: String) = fixas.pedidos.filter { it.startsWith(inicio) }

    @Test
    fun `titular ve a familia, renomeia, convida e cancela convite`() = runTest {
        entrar()
        val vm = familiaVm()
        val pronta = vm.estado.esperar { it.casa != null }
        assertTrue(pronta.ehTitular)
        assertEquals("Família Demo", pronta.nome)
        assertEquals(listOf("Ana Souza", "Beto Souza"), pronta.casa!!.membros.map { it.nome })
        assertEquals(listOf("vovo@exemplo.com"), pronta.casa!!.convites.map { it.email })

        vm.mudarNome("  ")
        vm.renomear()
        assertEquals(MensagemDeErro.DoApp(R.string.familia_nome_vazio), vm.estado.value.erro)
        vm.mudarNome(" Casa nova ")
        vm.renomear()
        val renomeada = vm.estado.esperar { it.aviso != null }
        assertEquals(Aviso(R.string.familia_nome_salvo), renomeada.aviso)
        assertNull(renomeada.erro)
        assertEquals("Casa nova", renomeada.casa!!.nome)
        assertEquals(listOf("""PUT /api/family {"nome":"Casa nova"}"""), pedidos("PUT /api/family"))

        vm.mudarEmail("sem-arroba")
        vm.convidar()
        assertEquals(MensagemDeErro.DoApp(R.string.familia_email_invalido), vm.estado.value.erro)
        assertTrue(pedidos("POST /api/family/invites").isEmpty())

        vm.mudarEmail(" tia@exemplo.com ")
        val estados = estadosDe(vm.estado)
        vm.convidar()
        val convidou = vm.estado.esperar { it.aviso == Aviso(R.string.familia_convite_criado) && !it.ocupado }
        assertTrue(estados.any { it.ocupado })
        assertEquals("", convidou.email)
        assertTrue(convidou.casa!!.convites.any { it.email == "tia@exemplo.com" })
        assertEquals(listOf("""POST /api/family/invites {"email":"tia@exemplo.com"}"""), pedidos("POST /api/family/invites"))

        val vovo = vm.estado.value.casa!!.convites.first { it.email == "vovo@exemplo.com" }
        vm.cancelarConvite(vovo)
        val cancelado = vm.estado.esperar { it.aviso == Aviso(R.string.familia_convite_cancelado) && !it.ocupado }
        assertTrue(cancelado.casa!!.convites.none { it.id == vovo.id })
        assertEquals(listOf("DELETE /api/family/invites/${vovo.id}"), pedidos("DELETE"))
    }

    @Test
    fun `titular convida para parceiro, cancela o convite, volta a membro e remove, sempre confirmando`() = runTest {
        entrar(
            "PUT /api/family/members/u-beto/role" to familia(pendente = true),
            "DELETE /api/family/members/u-beto" to familia(),
        )
        val vm = familiaVm()
        val beto = vm.estado.esperar { it.casa != null }.casa!!.membros.first { it.id == "u-beto" }

        vm.mudarPapel(beto)
        assertEquals(Confirmacao.ConvidarParceiro(beto), vm.estado.value.confirmacao)
        vm.desistir()
        assertTrue(pedidos("PUT /api/family/members").isEmpty())

        vm.mudarPapel(beto)
        vm.confirmar()
        val convidado = vm.estado.esperar { it.aviso != null && !it.ocupado }
        assertEquals(Aviso(R.string.familia_parceria_enviada, "Beto Souza"), convidado.aviso)
        assertEquals(true, convidado.casa!!.membros.first { it.id == "u-beto" }.parceriaPendente)

        // Convite de parceria pendente se desfaz sem perguntar.
        vm.mudarPapel(convidado.casa!!.membros.first { it.id == "u-beto" })
        assertNull(vm.estado.value.confirmacao)
        vm.estado.esperar { it.aviso == Aviso(R.string.familia_parceria_cancelada) && !it.ocupado }

        vm.mudarPapel(beto.copy(papel = PigguRole.PARCEIRO))
        assertEquals(Confirmacao.VoltarAMembro(beto.copy(papel = PigguRole.PARCEIRO)), vm.estado.value.confirmacao)
        vm.confirmar()
        vm.estado.esperar { it.aviso == Aviso(R.string.familia_papel_alterado) && !it.ocupado }
        assertEquals(
            listOf(
                """PUT /api/family/members/u-beto/role {"papel":"PARCEIRO"}""",
                """PUT /api/family/members/u-beto/role {"papel":"MEMBRO"}""",
                """PUT /api/family/members/u-beto/role {"papel":"MEMBRO"}""",
            ),
            pedidos("PUT /api/family/members"),
        )

        vm.pedirParaRemover(beto)
        assertEquals(Confirmacao.Remover(beto), vm.estado.value.confirmacao)
        vm.confirmar()
        vm.estado.esperar { it.aviso == Aviso(R.string.familia_pessoa_removida) && !it.ocupado }
        assertEquals(listOf("DELETE /api/family/members/u-beto"), pedidos("DELETE"))
    }

    @Test
    fun `membro ve a lista sem as acoes do titular`() = runTest {
        entrar("POST /api/auth/entrar" to entrarComo("u-beto", "Beto Souza", "MEMBRO"))
        val estado = familiaVm().estado.esperar { it.casa != null }
        assertFalse(estado.ehTitular)
        assertEquals("u-beto", estado.eu)
        assertFalse(estado.conviteDeParceria)
        assertEquals(2, estado.casa!!.membros.size)
    }

    @Test
    fun `quem foi convidado para parceiro recusa, ou aceita e entra de novo`() = runTest {
        entrar(
            "POST /api/auth/entrar" to entrarComo("u-beto", "Beto Souza", "MEMBRO"),
            "GET /api/family" to familia(pendente = true, planoCasal = true),
            "POST /api/family/partnership/decline" to familia(),
            "POST /api/family/partnership/accept" to RespostaFixa(204),
        )
        val vm = familiaVm()
        assertTrue(vm.estado.esperar { it.casa != null }.conviteDeParceria)

        vm.recusarParceria()
        val recusou = vm.estado.esperar { it.aviso != null && !it.ocupado }
        assertEquals(Aviso(R.string.familia_parceria_recusada), recusou.aviso)
        assertFalse(recusou.conviteDeParceria)
        assertEquals(listOf("POST /api/family/partnership/decline {}"), pedidos("POST /api/family/partnership"))

        vm.pedirParaAceitarParceria()
        vm.desistir()
        assertEquals(1, pedidos("POST /api/family/partnership").size)

        vm.pedirParaAceitarParceria()
        assertEquals(Confirmacao.AceitarParceria, vm.estado.value.confirmacao)
        vm.confirmar()
        rede.sessao.estado.esperar { it == EstadoDaSessao.Deslogado }
        assertEquals("POST /api/family/partnership/accept {}", pedidos("POST /api/family/partnership").last())
        // O site só encerra aqui (encerrarLocalmente): não avisa o servidor.
        assertTrue(pedidos("POST /api/auth/logout").isEmpty())
    }

    @Test
    fun `convite de outra familia aparece e aceitar encerra a sessao, ou mostra o erro`() = runTest {
        entrar(
            "GET /api/family/invites/mine" to RespostaFixa(
                200, """[{"id":"c9","email":"ana@demo.piggu.app","venceEm":"2026-11-01T00:00:00Z","familia":"Casa da Lu"}]""",
            ),
            "POST /api/family/invites/c9/accept" to RespostaFixa(204),
            "POST /api/family/invites/c8/accept" to RespostasFixas.erro(410, "CONVITE_VENCIDO", "Convite vencido."),
        )
        val vm = familiaVm()
        val convite = vm.estado.esperar { it.convitesParaMim.isNotEmpty() }.convitesParaMim.single()
        assertEquals("Casa da Lu", convite.familia)

        vm.pedirParaAceitarConvite(convite.copy(id = "c8"))
        vm.confirmar()
        assertEquals(MensagemDeErro.DoServidor("Convite vencido."), vm.estado.esperar { it.erro != null }.erro)
        assertTrue(rede.sessao.estado.value is EstadoDaSessao.Logado)

        vm.pedirParaAceitarConvite(convite)
        assertEquals(Confirmacao.AceitarConvite(convite), vm.estado.value.confirmacao)
        vm.confirmar()
        rede.sessao.estado.esperar { it == EstadoDaSessao.Deslogado }
        assertEquals(listOf("POST /api/family/invites/c8/accept {}", "POST /api/family/invites/c9/accept {}"), pedidos("POST /api/family/invites/c"))
    }

    @Test
    fun `sair da familia confirma, mostra o erro e depois sai da conta`() = runTest {
        var recusar = true
        entrar("POST /api/auth/entrar" to entrarComo("u-beto", "Beto Souza", "PARCEIRO"))
        val identidade = object : ServicoDeIdentidade by rede.identidade {
            override suspend fun sairDaFamilia(vazio: kotlinx.serialization.json.JsonObject) =
                if (recusar) throw ErroDaApi.DoServidor(409, null, "Titular não sai.", emptyList(), null) else Unit
        }
        val vm = familiaVm(identidade)
        vm.estado.esperar { it.casa != null }

        vm.pedirParaSair()
        assertEquals(Confirmacao.Sair, vm.estado.value.confirmacao)
        vm.confirmar()
        assertEquals(MensagemDeErro.DoServidor("Titular não sai."), vm.estado.esperar { it.erro != null && !it.ocupado }.erro)
        assertTrue(rede.sessao.estado.value is EstadoDaSessao.Logado)

        recusar = false
        vm.pedirParaSair()
        vm.confirmar()
        rede.sessao.estado.esperar { it == EstadoDaSessao.Deslogado }
        // Como o auth.sair() do site: avisa o servidor também.
        assertEquals(1, pedidos("POST /api/auth/logout").size)
    }

    @Test
    fun `historico junta dinheiro e pessoas, do mais novo ao mais velho, com o nome de quem fez`() = runTest {
        entrar(
            "GET /api/history" to RespostaFixa(
                200,
                """[{"id":7,"autor":"u-beto","acao":"APAGOU","entidade":"gasto","antes":"2026-09-10 · Pão · Lazer · 15.00","quando":"2026-10-10T12:00:00Z"},""" +
                    """{"id":5,"autor":"$SISTEMA","acao":"CRIOU","entidade":"conta-fixa","depois":"Aluguel","quando":"2026-10-01T09:00:00Z"}]""",
            ),
            "GET /api/family/history" to RespostaFixa(
                200,
                """[{"id":3,"autor":"u-saiu","acao":"MUDOU_PAPEL","entidade":"pessoa","antes":"Bia · MEMBRO","depois":"Bia · PARCEIRO","quando":"2026-10-09T12:00:00Z"}]""",
            ),
        )
        val vm = familiaVm()
        assertNull(vm.estado.esperar { it.casa != null }.historico)
        assertTrue(pedidos("GET /api/history").isEmpty()) // só busca quando a pessoa pede

        vm.verHistorico()
        val linhas = vm.estado.esperar { it.historico != null }.historico!!
        assertEquals(listOf("gasto-7", "pessoa-3", "conta-fixa-5"), linhas.map { it.chave })
        assertEquals(listOf(Autor.Pessoa("Beto Souza"), Autor.ExMembro, Autor.Piggu), linhas.map { it.autor })
        assertEquals(AcaoDeAuditoria.APAGOU, linhas[0].acao)
        assertEquals("2026-09-10 · Pão · Lazer · 15.00", linhas[0].mudanca)
        assertEquals("Bia · MEMBRO → Bia · PARCEIRO", linhas[1].mudanca)
        assertEquals("Aluguel", linhas[2].mudanca)
        assertEquals("10/10/2026, 09:00", linhas[0].quando) // hora de São Paulo
        assertFalse(vm.estado.value.carregandoHistorico)
    }

    @Test
    fun `historico vazio quando os dois servicos nao respondem`() = runTest {
        entrar(
            "GET /api/history" to RespostasFixas.erro(503, "FORA", "Fora do ar."),
            "GET /api/family/history" to RespostasFixas.erro(503, "FORA", "Fora do ar."),
        )
        val vm = familiaVm()
        vm.estado.esperar { it.casa != null }
        vm.verHistorico()
        assertEquals(emptyList<LinhaDoHistorico>(), vm.estado.esperar { it.historico != null }.historico)
    }

    @Test
    fun `e-mail nao confirmado mostra o texto do servidor e guarda o e-mail digitado`() = runTest {
        val texto = "Confirme seu e-mail pelo link que enviamos (ou entre uma vez com o Google desse e-mail) para fazer isso."
        entrar("POST /api/family/invites" to RespostasFixas.erro(403, "EMAIL_NAO_CONFIRMADO", texto))
        val vm = familiaVm()
        vm.estado.esperar { it.casa != null }
        vm.mudarEmail("tia@exemplo.com")
        vm.convidar()
        val recusado = vm.estado.esperar { it.erro != null && !it.ocupado }
        assertEquals(MensagemDeErro.DoServidor(texto), recusado.erro)
        assertNull(recusado.aviso)
        assertEquals("tia@exemplo.com", recusado.email)
    }

    @Test
    fun `sem internet mostra o guardado com a hora, ou o erro quando nao ha nada`() = runTest {
        entrar()
        val semRede = object : ServicoDeIdentidade by rede.identidade {
            override suspend fun verFamilia(): Familia = throw ErroDaApi.SemInternet(IOException("sem rede"))
            override suspend fun convitesParaMim() = throw ErroDaApi.SemInternet(IOException("sem rede"))
        }
        conectado.value = false
        val vazio = familiaVm(semRede).estado.esperar { it.familia is Carga.Falhou }
        assertEquals(FalhaAoCarregar.SemInternet, falhaDe((vazio.familia as Carga.Falhou).erro))
        assertFalse(vazio.conectado)

        conectado.value = true
        familiaVm().estado.esperar { it.casa != null }
        assertNotNull(guardado.respostas["familia"])

        conectado.value = false
        val comGuardado = familiaVm(semRede).estado.esperar { it.guardadoEm != null }
        assertEquals("Família Demo", comGuardado.casa!!.nome)
        assertTrue(comGuardado.convitesParaMim.isEmpty())

        // Ação sem rede: o aviso de sem conexão.
        val acao = familiaVm(
            object : ServicoDeIdentidade by rede.identidade {
                override suspend fun renomearFamilia(pedido: br.com.piggu.core.model.PedidoDeNome): Familia =
                    throw ErroDaApi.SemInternet(IOException("sem rede"))
            },
        )
        acao.estado.esperar { it.casa != null }
        acao.mudarNome("Outra")
        acao.renomear()
        assertEquals(MensagemDeErro.DoApp(br.com.piggu.core.network.R.string.erro_sem_conexao), acao.estado.esperar { it.erro != null }.erro)
    }

    @Test
    fun `erro do servidor ao carregar pede tentar de novo`() = runTest {
        entrar()
        var falhar = true
        val instavel = object : ServicoDeIdentidade by rede.identidade {
            override suspend fun verFamilia(): Familia =
                if (falhar) throw ErroDaApi.DoServidor(503, null, "Fora do ar.", emptyList(), null) else rede.identidade.verFamilia()
        }
        val vm = familiaVm(instavel)
        assertEquals(FalhaAoCarregar.DoServidor, falhaDe((vm.estado.esperar { it.familia is Carga.Falhou }.familia as Carga.Falhou).erro))
        falhar = false
        vm.tentarDeNovo()
        assertEquals("Família Demo", vm.estado.esperar { it.casa != null }.casa!!.nome)
    }
}
