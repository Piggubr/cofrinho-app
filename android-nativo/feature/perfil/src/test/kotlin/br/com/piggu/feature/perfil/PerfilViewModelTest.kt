package br.com.piggu.feature.perfil

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import br.com.piggu.core.data.AutenticacaoDaSessao
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.ListasParaLancar
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.modulosLigados
import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.datastore.Cifra
import br.com.piggu.core.datastore.CofreDaSessao
import br.com.piggu.core.datastore.PreferenciasDoAparelho
import br.com.piggu.core.designsystem.ModoDeCor
import br.com.piggu.core.designsystem.VarianteDeCor
import br.com.piggu.core.model.JsonDoPiggu
import br.com.piggu.core.model.ModuloDeEstiloDeVida
import br.com.piggu.core.model.PedidoDeAlteracaoDeUsuario
import br.com.piggu.core.model.PedidoSobreVoce
import br.com.piggu.core.model.PigguRole
import br.com.piggu.core.model.Plano
import br.com.piggu.core.network.AvisoDeVersao
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ModuloDaRede
import br.com.piggu.core.network.ServicoDeIdentidade
import br.com.piggu.core.network.URL_DA_API
import br.com.piggu.core.testing.ApiDaDemo
import br.com.piggu.core.testing.DIA_DAS_CAPTURAS
import br.com.piggu.core.testing.FilaNaMemoria
import br.com.piggu.core.testing.RespostaFixa
import br.com.piggu.core.testing.RespostasFixas
import br.com.piggu.core.testing.esperar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap

private object SemCifra : Cifra {
    override fun cifrar(claro: ByteArray) = claro
    override fun decifrar(cifrado: ByteArray) = cifrado
}

private class GuardadoNaMemoria : RespostasGuardadasDao {
    val respostas = ConcurrentHashMap<String, RespostaGuardada>()
    override suspend fun ler(chave: String) = respostas[chave]
    override suspend fun guardar(resposta: RespostaGuardada) { respostas[resposta.chave] = resposta }
    override suspend fun apagarTudo() = respostas.clear()
}

/**
 * Perfil (profile.spec.ts e autorizacoes-de-ia.spec.ts) com a API falsa da demonstração: a Ana,
 * titular com Premium, e o Beto, membro. A rede é a do redeDeTeste, montada aqui para o teste
 * ver o cofre da sessão (o refresh da senha).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PerfilViewModelTest {
    @get:Rule val pasta = TemporaryFolder()
    private val loja = ViewModelStore()
    private var criados = 0
    private lateinit var fixas: RespostasFixas
    private lateinit var sessao: Sessao
    private lateinit var cofre: CofreDaSessao
    private lateinit var retrofit: retrofit2.Retrofit
    private lateinit var preferencias: PreferenciasDoAparelho
    private lateinit var icones: IconesDoApp
    private val conectado = MutableStateFlow(true)

    // Sem resetMain: resposta atrasada da API falsa (outra thread) iria para um Main desfeito e derrubava o teste seguinte no CI.
    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun devolver() = loja.clear()

    private val identidade get() = ModuloDaRede.identidade(retrofit)
    private val financas get() = ModuloDaRede.financas(retrofit)

    private suspend fun TestScope.entrar(vararg respostas: Pair<String, RespostaFixa>, lembrar: Boolean = false) {
        fixas = RespostasFixas(*respostas)
        cofre = CofreDaSessao(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { pasta.newFile("s.preferences_pb") }), SemCifra)
        lateinit var servico: ServicoDeIdentidade
        sessao = Sessao({ servico }, cofre, GuardadoNaMemoria(), backgroundScope)
        val cliente = ModuloDaRede.okHttp("2.0.0", AvisoDeVersao(), AutenticacaoDaSessao(sessao), linkedSetOf(fixas, ApiDaDemo(DIA_DAS_CAPTURAS)))
        retrofit = ModuloDaRede.retrofit(URL_DA_API, cliente, JsonDoPiggu)
        servico = ModuloDaRede.identidade(retrofit)
        preferencias = PreferenciasDoAparelho(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { pasta.newFile("a.preferences_pb") }))
        icones = IconesDoApp(ApplicationProvider.getApplicationContext(), preferencias)
        sessao.entrarComSenha("ana@demo.piggu.app", "senha123", lembrar)
    }

    /** Muda a Ana na sessão (plano, papel) antes de abrir o Perfil. */
    private suspend fun comoAna(mudar: (br.com.piggu.core.model.Usuario) -> br.com.piggu.core.model.Usuario) = sessao.entrou(mudar(sessao.usuario!!))

    private fun perfil(servico: ServicoDeIdentidade = identidade): PerfilViewModel {
        val cache = CacheDeRespostas(GuardadoNaMemoria(), DIA_DAS_CAPTURAS)
        return ViewModelProvider.create(
            loja,
            viewModelFactory {
                initializer {
                    PerfilViewModel(sessao, servico, financas, ListasParaLancar(cache, financas, servico), preferencias, icones, FilaNaMemoria(), conectado)
                }
            },
        )["vm${criados++}", PerfilViewModel::class]
    }

    /** O Perfil aberto, com as moedas e a família já carregadas. */
    private suspend fun PerfilViewModel.pronto(): EstadoDoPerfil = estado.esperar { it.moedas.isNotEmpty() && it.temCompanhia }

    private fun pedidos(inicio: String) = fixas.pedidos.filter { it.startsWith(inicio) }

    @Test
    fun `apelido vazio pede o nome e o salvo vai para a sessao`() = runTest {
        entrar()
        val enviados = mutableListOf<PedidoSobreVoce>()
        val perfil = perfil(
            object : ServicoDeIdentidade by identidade {
                override suspend fun sobreVoce(pedido: PedidoSobreVoce) = sessao.usuario!!.copy(apelido = pedido.apelido).also { enviados += pedido }
            },
        )
        perfil.pronto()
        perfil.mudarApelido("   ")
        perfil.salvarApelido()
        assertEquals(MensagemDeErro.DoApp(R.string.perfil_apelido_vazio), perfil.estado.value.erro)
        assertTrue(enviados.isEmpty())

        perfil.mudarApelido("  Aninha ")
        perfil.salvarApelido()
        val salvo = perfil.estado.esperar { it.aviso == R.string.perfil_apelido_salvo }
        assertEquals(listOf(PedidoSobreVoce("Aninha")), enviados)
        assertEquals("Aninha", sessao.usuario!!.apelido)
        assertNull(salvo.erro)
    }

    @Test
    fun `moeda e cotacao salvam a cada escolha e voltam ao gravado quando o servidor recusa`() = runTest {
        entrar()
        val perfil = perfil()
        val aberto = perfil.pronto()
        assertEquals("BRL", aberto.preferencias.moeda)
        assertFalse(aberto.preferencias.mostrarCotacao)

        perfil.estado.test {
            perfil.mudarMostrarCotacao(true)
            var estado = awaitItem()
            while (estado.ocupado || !estado.preferencias.mostrarCotacao) estado = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        perfil.estado.esperar { !it.ocupado }
        perfil.mudarMoedaCotacao("EUR")
        perfil.estado.esperar { !it.ocupado && sessao.usuario!!.preferencias?.moedaCotacao == "EUR" }
        assertEquals(
            listOf(
                """PUT /api/auth/me/preferences {"moeda":"BRL","moedaCotacao":"BRL","moedaConversao":"USD","mostrarCotacao":true}""",
                """PUT /api/auth/me/preferences {"moeda":"BRL","moedaCotacao":"EUR","moedaConversao":"USD","mostrarCotacao":true}""",
            ),
            pedidos("PUT /api/auth/me/preferences"),
        )
        assertEquals(true, sessao.usuario!!.preferencias?.mostrarCotacao)
    }

    @Test
    fun `moeda recusada volta para a gravada e mostra o erro`() = runTest {
        entrar("PUT /api/auth/me/preferences" to RespostasFixas.erro(422, "VALIDACAO", "Moeda não suportada."))
        val perfil = perfil()
        perfil.pronto()
        perfil.mudarMoeda("USD")
        val recusada = perfil.estado.esperar { it.erro != null }
        assertEquals(MensagemDeErro.DoServidor("Moeda não suportada."), recusada.erro)
        assertEquals("BRL", recusada.preferencias.moeda)
        assertFalse(recusada.ocupado)
    }

    @Test
    fun `avisos por e-mail e no celular, com e sem a permissao do Android`() = runTest {
        entrar()
        val perfil = perfil()
        assertTrue(perfil.pronto().avisos.email)

        perfil.mudarAvisoPorEmail(false)
        perfil.estado.esperar { it.aviso == R.string.perfil_avisos_salvos }
        perfil.mensagemVista()

        perfil.mudarAvisoNoCelular(ligado = true, permitido = true)
        perfil.estado.esperar { it.aviso == R.string.perfil_avisos_salvos }
        assertEquals(true, sessao.usuario!!.avisos?.push)
        perfil.mensagemVista()

        // Recusou a permissão: o celular fica desligado, o resto salva e o recado explica.
        perfil.mudarAvisoNoCelular(ligado = false, permitido = true)
        perfil.estado.esperar { it.aviso != null }
        perfil.mensagemVista()
        perfil.mudarAvisoNoCelular(ligado = true, permitido = false)
        val recusado = perfil.estado.esperar { it.erro != null && !it.ocupado }
        assertEquals(MensagemDeErro.DoApp(R.string.perfil_avisos_sem_permissao), recusado.erro)
        assertFalse(recusado.avisos.push)
        assertNull(recusado.aviso)
        assertEquals(
            listOf(
                """PUT /api/auth/me/alerts {"email":false,"push":false}""",
                """PUT /api/auth/me/alerts {"email":false,"push":true}""",
                """PUT /api/auth/me/alerts {"email":false,"push":false}""",
                """PUT /api/auth/me/alerts {"email":false,"push":false}""",
            ),
            pedidos("PUT /api/auth/me/alerts"),
        )
    }

    @Test
    fun `compartilhamento so libera tudo quando ha parceiro`() = runTest {
        // A demo tem o Beto como membro: sem parceiro, "ver tudo" não vai ligado.
        entrar()
        val semParceiro = perfil()
        assertNull(semParceiro.pronto().parceiro)
        semParceiro.mudarMostrarTudoAoParceiro(true)
        semParceiro.estado.esperar { it.aviso == R.string.perfil_compartilhamento_salvo }
        assertEquals(
            listOf("""PUT /api/auth/me/sharing {"mostrarTudoAoParceiro":false,"compartilharPorPadrao":false}"""),
            pedidos("PUT /api/auth/me/sharing"),
        )
    }

    @Test
    fun `com parceiro na familia, ver tudo e nascer compartilhado vao ao servidor`() = runTest {
        val familia = """{"id":"f","nome":"Casa","plano":"PREMIUM","planoFamilia":true,"membros":[
            {"id":"u-ana","nome":"Ana Souza","papel":"TITULAR","premium":true},
            {"id":"u-caio","nome":"Caio","papel":"PARCEIRO","premium":true}],"convites":[]}"""
        entrar("GET /api/family" to RespostaFixa(200, familia))
        val perfil = perfil()
        assertEquals("Caio", perfil.pronto().parceiro?.nome)
        perfil.mudarMostrarTudoAoParceiro(true)
        perfil.estado.esperar { it.aviso == R.string.perfil_compartilhamento_salvo }
        perfil.mudarCompartilharPorPadrao(true)
        perfil.estado.esperar { !it.ocupado && sessao.usuario!!.compartilhamento?.compartilharPorPadrao == true }
        assertEquals(
            """PUT /api/auth/me/sharing {"mostrarTudoAoParceiro":true,"compartilharPorPadrao":true}""",
            pedidos("PUT /api/auth/me/sharing").last(),
        )
        assertEquals(true, sessao.usuario!!.compartilhamento?.mostraTudoAoParceiro)
    }

    @Test
    fun `telas do menu mandam os ligados na ordem e o Mais passa a esconder o desligado`() = runTest {
        entrar()
        val perfil = perfil()
        assertEquals(MODULOS.toSet(), perfil.pronto().modulos)
        perfil.alternarModulo(ModuloDeEstiloDeVida.FILMES)
        perfil.estado.esperar { !it.ocupado && sessao.usuario!!.modulosLigados != null }
        assertEquals(
            listOf("""PUT /api/auth/me/modules {"modulos":["compras","lugares","fotos","premios","girls-math"]}"""),
            pedidos("PUT /api/auth/me/modules"),
        )
        assertEquals(listOf("compras", "lugares", "fotos", "premios", "girls-math"), sessao.usuario!!.modulosLigados)
        assertFalse(ModuloDeEstiloDeVida.FILMES in perfil.estado.value.modulos)
    }

    @Test
    fun `cor e icone do Premium sem Premium convidam, e o modo e a cor ficam no aparelho`() = runTest {
        entrar()
        comoAna { it.copy(plano = Plano.GRATUITO) }
        val perfil = perfil()
        perfil.pronto()

        perfil.escolherCor(VarianteDeCor.Lilas)
        assertTrue(perfil.estado.value.irParaOPremium)
        perfil.premiumAberto()
        perfil.escolherIcone(IconeDoApp.Azul)
        assertTrue(perfil.estado.value.irParaOPremium)
        perfil.premiumAberto()
        assertEquals(null, preferencias.cor.first())
        assertEquals(IconeDoApp.Padrao, icones.atual())

        perfil.escolherCor(VarianteDeCor.Menta)
        perfil.escolherModo(ModoDeCor.Escuro)
        val escolhido = perfil.estado.esperar { it.cor == VarianteDeCor.Menta && it.modo == ModoDeCor.Escuro }
        assertFalse(escolhido.irParaOPremium)
        assertEquals("escuro", preferencias.modo.first())

        // Quem não assina pela família só recebe o recado.
        comoAna { it.copy(role = PigguRole.MEMBRO) }
        perfil.escolherCor(VarianteDeCor.Vinho)
        assertFalse(perfil.estado.value.irParaOPremium)
        assertEquals(R.string.perfil_premium_do_titular, perfil.estado.value.aviso)
    }

    @Test
    fun `com Premium a cor escolhida desliga as cores do celular`() = runTest {
        entrar()
        preferencias.mudarCoresDoCelular(true)
        val perfil = perfil()
        perfil.pronto()
        perfil.escolherCor(VarianteDeCor.Lilas)
        val estado = perfil.estado.esperar { it.cor == VarianteDeCor.Lilas && !it.coresDoCelular }
        assertFalse(estado.irParaOPremium)
    }

    @Test
    fun `trocar a senha manda o refresh deste aparelho para continuar conectado`() = runTest {
        entrar(lembrar = true)
        val refresh = cofre.refresh()
        assertEquals("refresh-da-demonstracao", refresh)
        val perfil = perfil()
        perfil.pronto()

        perfil.mudarSenhaNova("novasenha1")
        perfil.mudarSenhaRepetida("novasenha1")
        perfil.salvarSenha()
        assertEquals(MensagemDeErro.DoApp(R.string.perfil_senha_informe_a_atual), perfil.estado.value.senha.erro)

        perfil.mudarSenhaAtual("senha123")
        perfil.mudarSenhaRepetida("outrasenha1")
        perfil.salvarSenha()
        assertEquals(MensagemDeErro.DoApp(R.string.perfil_senha_diferentes), perfil.estado.value.senha.erro)

        perfil.mudarSenhaRepetida("novasenha1")
        perfil.salvarSenha()
        val trocada = perfil.estado.esperar { it.senha.aviso != null }
        assertEquals(R.string.perfil_senha_trocada, trocada.senha.aviso)
        assertEquals("", trocada.senha.nova)
        assertEquals(
            listOf("""PUT /api/auth/me/senha {"senhaAtual":"senha123","novaSenha":"novasenha1","refreshToken":"$refresh"}"""),
            pedidos("PUT /api/auth/me/senha"),
        )
        // Com o refresh guardado não precisa entrar de novo.
        assertEquals(1, pedidos("POST /api/auth/entrar").size)
    }

    @Test
    fun `sem continuar conectado, trocar a senha manda o refresh da memoria e nao entra de novo`() = runTest {
        entrar(lembrar = false)
        val perfil = perfil()
        perfil.pronto()
        perfil.mudarSenhaAtual("senha123")
        perfil.mudarSenhaNova("novasenha1")
        perfil.mudarSenhaRepetida("novasenha1")
        perfil.salvarSenha()
        perfil.estado.esperar { it.senha.aviso == R.string.perfil_senha_trocada }
        // O refresh só na memória vai junto do mesmo jeito: a sessão deste aparelho continua.
        assertEquals(
            listOf("""PUT /api/auth/me/senha {"senhaAtual":"senha123","novaSenha":"novasenha1","refreshToken":"refresh-da-demonstracao"}"""),
            pedidos("PUT /api/auth/me/senha"),
        )
        assertEquals(1, pedidos("POST /api/auth/entrar").size)
    }

    @Test
    fun `senha recusada pelo servidor aparece no cartao`() = runTest {
        entrar("PUT /api/auth/me/senha" to RespostasFixas.erro(422, "SENHA_ATUAL_ERRADA", "A senha atual nao confere."))
        val perfil = perfil()
        perfil.pronto()
        perfil.mudarSenhaAtual("errada123")
        perfil.mudarSenhaNova("novasenha1")
        perfil.mudarSenhaRepetida("novasenha1")
        perfil.salvarSenha()
        val recusada = perfil.estado.esperar { it.senha.erro != null }
        assertEquals(MensagemDeErro.DoServidor("A senha atual nao confere."), recusada.senha.erro)
        assertFalse(recusada.senha.salvando)
        assertEquals("novasenha1", recusada.senha.nova)
    }

    @Test
    fun `exportar entrega o JSON dos dados ao arquivo escolhido`() = runTest {
        entrar()
        val perfil = perfil()
        perfil.pronto()
        var gravado = ""
        perfil.exportar { gravado = it.decodeToString() }
        val pronto = perfil.estado.esperar { it.aviso == R.string.perfil_dados_salvos }
        assertTrue(gravado.contains("Dados de exemplo da demonstração"))
        assertFalse(pronto.ocupado)
    }

    @Test
    fun `retirar as autorizacoes da IA chama as rotas de consentimento`() = runTest {
        entrar(
            "DELETE /api/receipts/consent" to RespostaFixa(204),
            "DELETE /api/assistant/consent" to RespostasFixas.erro(503, "INDISPONIVEL", "Tente de novo em instantes."),
        )
        val perfil = perfil()
        perfil.pronto()
        perfil.retirarAutorizacaoDaNota()
        perfil.estado.esperar { it.aviso == R.string.perfil_ia_nota_retirada }
        perfil.mensagemVista()
        perfil.retirarAutorizacaoDoAssistente()
        val falhou = perfil.estado.esperar { it.erro != null }
        assertEquals(MensagemDeErro.DoServidor("Tente de novo em instantes."), falhou.erro)
        assertEquals(listOf("DELETE /api/receipts/consent", "DELETE /api/assistant/consent"), pedidos("DELETE /api/"))
    }

    @Test
    fun `contas da instalacao so para o administrador`() = runTest {
        entrar()
        val titular = perfil()
        titular.pronto()
        assertNull(titular.estado.value.contas)
        assertTrue(pedidos("GET /api/users").isEmpty())

        comoAna { it.copy(role = PigguRole.ADMIN) }
        val alteradas = mutableListOf<Pair<String, PedidoDeAlteracaoDeUsuario>>()
        val admin = perfil(
            object : ServicoDeIdentidade by identidade {
                // A demo não muda contas: devolve a própria, e a lista recarrega da demo.
                override suspend fun alterarUsuario(id: String, mudancas: PedidoDeAlteracaoDeUsuario) =
                    sessao.usuario!!.also { alteradas += id to mudancas }
            },
        )
        val contas = admin.estado.esperar { it.contas is Carga.Pronto }.contas as Carga.Pronto
        assertEquals(listOf("Ana Souza", "Beto Souza"), contas.valor.map { it.nome })
        val beto = contas.valor.last()

        admin.alternarAtivo(beto)
        admin.alterarPapel(beto, PigguRole.PARCEIRO)
        // A lista recarregada é igual (a demo não muda): o estado não emite de novo, então espera os pedidos.
        withContext(Dispatchers.Default) { withTimeout(30_000) { while (pedidos("GET /api/users").size < 3) delay(10) } }
        assertEquals(
            listOf("u-beto" to PedidoDeAlteracaoDeUsuario(ativo = false), "u-beto" to PedidoDeAlteracaoDeUsuario(role = PigguRole.PARCEIRO)),
            alteradas,
        )
    }
}
