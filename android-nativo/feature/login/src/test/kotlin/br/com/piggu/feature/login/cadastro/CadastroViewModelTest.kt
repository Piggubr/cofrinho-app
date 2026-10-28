package br.com.piggu.feature.login.cadastro
import br.com.piggu.core.testing.sessaoDeTeste
import br.com.piggu.core.testing.esperar

import app.cash.turbine.test
import br.com.piggu.core.data.VERSAO_DOS_TERMOS
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.testing.RespostasFixas
import br.com.piggu.feature.login.R
import br.com.piggu.core.testing.sessaoDeTeste
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import br.com.piggu.core.network.R as Rede
import br.com.piggu.core.ui.R as Comum

/** O cadastro de login.spec.ts, com a sessão de verdade e a API falsa. */
@OptIn(ExperimentalCoroutinesApi::class)
class CadastroViewModelTest {
    @get:Rule val pasta = TemporaryFolder()

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun devolver() = Dispatchers.resetMain()

    private val pedidosDeCadastro = { fixas: RespostasFixas -> fixas.pedidos.filter { it.startsWith("POST /api/auth/cadastro") } }

    private fun TestScope.preenchido(fixas: RespostasFixas = RespostasFixas()) =
        CadastroViewModel(sessaoDeTeste(pasta.root, fixas)).apply {
            mudarNome(" Ana ")
            mudarNascimento("1990-05-20")
            mudarEmail(" ana@piggu.test ")
            mudarSenha("cofrinho123")
            mudarConfirmacao("cofrinho123")
            mudarTelefone("(11) 99999-8888")
            mudarDocumento("52998224725")
            mudarAceite(true)
        }

    private fun CadastroViewModel.recusou(campo: CampoDoCadastro, texto: Int) {
        criar()
        assertEquals(campo, estado.value.campoComProblema)
        assertEquals(MensagemDeErro.DoApp(texto), estado.value.erro)
    }

    @Test
    fun `cadastro valido manda o pedido com a versao dos termos e avisa o e-mail`() = runTest {
        val fixas = RespostasFixas()
        val viewModel = preenchido(fixas)
        assertEquals("529.982.247-25", viewModel.estado.value.documento)
        viewModel.estado.test {
            viewModel.criar()
            val criado = ate { it.criadaPara != null }
            assertEquals("ana@piggu.test", criado.criadaPara)
            assertNull(criado.erro)
        }
        assertEquals(
            listOf(
                "POST /api/auth/cadastro " +
                    """{"nome":"Ana","email":"ana@piggu.test","senha":"cofrinho123","pais":"BR","documento":"52998224725",""" +
                    """"telefone":"+55 (11) 99999-8888","nascimento":"1990-05-20","versaoDosTermos":"$VERSAO_DOS_TERMOS"}""",
            ),
            pedidosDeCadastro(fixas),
        )
        // Volta para "Entrar": a senha e o aceite não ficam.
        viewModel.avisado()
        assertEquals("", viewModel.estado.value.senha)
        assertEquals(false, viewModel.estado.value.aceitou)
        assertNull(viewModel.estado.value.criadaPara)
    }

    @Test
    fun `confere cada campo na ordem da tela antes de chamar o backend`() = runTest {
        val fixas = RespostasFixas()
        val viewModel = CadastroViewModel(sessaoDeTeste(pasta.root, fixas))
        viewModel.mudarNome("  ")
        viewModel.recusou(CampoDoCadastro.Nome, R.string.cadastro_falta_nome)
        viewModel.mudarNome("Ana")
        viewModel.recusou(CampoDoCadastro.Nascimento, Comum.string.nascimento_vazio)
        viewModel.mudarNascimento(java.time.LocalDate.now().minusYears(12).toString())
        viewModel.recusou(CampoDoCadastro.Nascimento, Comum.string.nascimento_menor_de_idade)
        viewModel.mudarNascimento("1990-05-20")
        viewModel.mudarEmail("ana.piggu.test")
        viewModel.recusou(CampoDoCadastro.Email, R.string.cadastro_confira_email)
        viewModel.mudarEmail("ana@piggu.test")
        viewModel.mudarSenha("curta")
        viewModel.recusou(CampoDoCadastro.Senha, R.string.cadastro_senha_curta)
        viewModel.mudarSenha("abcdefgh")
        viewModel.recusou(CampoDoCadastro.Senha, R.string.cadastro_senha_letras_e_numeros)
        viewModel.mudarSenha("cofrinho123")
        viewModel.mudarConfirmacao("cofrinho124")
        viewModel.recusou(CampoDoCadastro.Confirmacao, R.string.cadastro_senhas_diferentes)
        viewModel.mudarConfirmacao("cofrinho123")
        viewModel.recusou(CampoDoCadastro.Telefone, R.string.cadastro_falta_celular)
        viewModel.mudarTelefone("(11) 99999-8888")
        viewModel.recusou(CampoDoCadastro.Documento, R.string.cadastro_falta_documento)
        viewModel.mudarDocumento("529.982.247-26")
        viewModel.recusou(CampoDoCadastro.Documento, R.string.cadastro_cpf_invalido)
        viewModel.mudarDocumento("52998224725")
        viewModel.recusou(CampoDoCadastro.Aceite, R.string.cadastro_falta_aceite)

        assertEquals(11, viewModel.estado.value.tentativas)
        assertEquals(emptyList<String>(), pedidosDeCadastro(fixas))
    }

    @Test
    fun `senha igual ao e-mail e longa demais tambem param`() = runTest {
        val viewModel = preenchido()
        viewModel.mudarEmail("ana1@piggu.test")
        viewModel.mudarSenha("Ana1@piggu.test")
        viewModel.recusou(CampoDoCadastro.Senha, R.string.cadastro_senha_igual_ao_email)
        viewModel.mudarSenha("a1" + "x".repeat(71))
        viewModel.recusou(CampoDoCadastro.Senha, R.string.cadastro_senha_longa)
    }

    @Test
    fun `o pais onde mora leva o DDI junto, e o CPF vale fora do Brasil`() = runTest {
        val fixas = RespostasFixas()
        val viewModel = preenchido(fixas)
        viewModel.mudarPais("PT")
        assertEquals("PT", viewModel.estado.value.paisDoDdi)
        viewModel.mudarTelefone("912 345 678")
        viewModel.estado.test {
            viewModel.criar()
            ate { it.criadaPara != null }
        }
        val pedido = pedidosDeCadastro(fixas).single()
        assert(""""pais":"PT","documento":"52998224725","telefone":"+351 912 345 678"""" in pedido) { pedido }
    }

    @Test
    fun `VALIDACAO do servidor vai para o campo dele`() = runTest {
        val fixas = RespostasFixas("POST /api/auth/cadastro" to validacao("telefone", "Confira o celular."))
        val viewModel = preenchido(fixas)
        viewModel.estado.test {
            viewModel.criar()
            val recusado = ate { !it.enviando && it.erro != null }
            assertEquals(CampoDoCadastro.Telefone, recusado.campoComProblema)
            assertEquals(MensagemDeErro.DoServidor("Confira o celular."), recusado.erro)
            assertNull(recusado.criadaPara)
        }
    }

    @Test
    fun `SENHA_FRACA do servidor vai para a senha`() = runTest {
        val fixas = RespostasFixas("POST /api/auth/cadastro" to RespostasFixas.erro(422, "SENHA_FRACA", "Essa senha é fácil demais."))
        val viewModel = preenchido(fixas)
        viewModel.estado.test {
            viewModel.criar()
            val recusado = ate { !it.enviando && it.erro != null }
            assertEquals(CampoDoCadastro.Senha, recusado.campoComProblema)
            assertEquals(MensagemDeErro.DoServidor("Essa senha é fácil demais."), recusado.erro)
        }
    }

    @Test
    fun `sem internet avisa no topo`() = runTest {
        val viewModel = CadastroViewModel(sessaoSemInternet(pasta.root))
        viewModel.mudarNome("Ana")
        viewModel.mudarNascimento("1990-05-20")
        viewModel.mudarEmail("ana@piggu.test")
        viewModel.mudarSenha("cofrinho123")
        viewModel.mudarConfirmacao("cofrinho123")
        viewModel.mudarTelefone("11999998888")
        viewModel.mudarDocumento("52998224725")
        viewModel.mudarAceite(true)
        viewModel.estado.test {
            viewModel.criar()
            val recusado = ate { !it.enviando && it.erro != null }
            assertNull(recusado.campoComProblema)
            assertEquals(MensagemDeErro.DoApp(Rede.string.erro_sem_conexao), recusado.erro)
        }
    }
}
