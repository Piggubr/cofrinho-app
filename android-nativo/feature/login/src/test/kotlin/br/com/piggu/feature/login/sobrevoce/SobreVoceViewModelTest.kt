package br.com.piggu.feature.login.sobrevoce
import br.com.piggu.core.testing.sessaoDeTeste
import br.com.piggu.core.testing.esperar

import app.cash.turbine.test
import br.com.piggu.core.data.EstadoDaSessao
import br.com.piggu.core.data.pendencia
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.testing.RespostaFixa
import br.com.piggu.core.testing.RespostasFixas
import br.com.piggu.feature.login.R
import br.com.piggu.feature.login.cadastro.ate
import br.com.piggu.feature.login.cadastro.usuario
import br.com.piggu.feature.login.cadastro.usuarioJson
import br.com.piggu.core.testing.sessaoDeTeste
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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
import br.com.piggu.core.ui.R as Comum

/** sobre-voce.spec.ts, com a sessão de verdade; a demo não responde /me/sobre-voce. */
@OptIn(ExperimentalCoroutinesApi::class)
class SobreVoceViewModelTest {
    @get:Rule val pasta = TemporaryFolder()

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun devolver() = Dispatchers.resetMain()

    private val respondeCompleto = RespostasFixas(
        "PUT /api/me/sobre-voce" to RespostaFixa(200, usuarioJson(""","apelido":"Tita","nascimento":"1990-05-10","cadastroCompleto":true""")),
    )

    @Test
    fun `quem ja tem nascimento, CPF e celular so manda o apelido`() = runTest {
        val sessao = sessaoDeTeste(pasta.root, respondeCompleto)
        sessao.entrou(usuario(""","apelido":"Tita","nascimento":"1990-05-10","cadastroCompleto":false"""))
        val viewModel = SobreVoceViewModel(sessao)
        with(viewModel.estado.value) {
            assertEquals("Tita", apelido)
            assertFalse(pedeNascimento || pedeCpf || pedeCelular)
        }
        sessao.estado.test {
            viewModel.continuar()
            ate { (it as? EstadoDaSessao.Logado)?.usuario?.cadastroCompleto == true }
        }
        assertEquals(listOf("""PUT /api/me/sobre-voce {"apelido":"Tita"}"""), respondeCompleto.pedidos)
        // A sessão já tem o perfil completo: o app sai da pendência sozinho.
        assertNull(sessao.usuario!!.pendencia)
    }

    @Test
    fun `quem entrou pelo Google sem nada confere tudo e manda tudo`() = runTest {
        val sessao = sessaoDeTeste(pasta.root, respondeCompleto)
        sessao.entrou(usuario(""","pais":"PT","cpfInformado":false,"celularInformado":false,"cadastroCompleto":false"""))
        val viewModel = SobreVoceViewModel(sessao)
        with(viewModel.estado.value) {
            assertEquals("Titular", apelido)
            assertTrue(pedeNascimento && pedeCpf && pedeCelular)
            assertEquals("PT", paisDoDdi)
        }
        fun recusou(campo: CampoSobreVoce, texto: Int) {
            viewModel.continuar()
            assertEquals(campo, viewModel.estado.value.campoComProblema)
            assertEquals(MensagemDeErro.DoApp(texto), viewModel.estado.value.erro)
        }
        viewModel.mudarApelido(" ")
        recusou(CampoSobreVoce.Apelido, R.string.cadastro_falta_nome)
        viewModel.mudarApelido(" Ti ")
        recusou(CampoSobreVoce.Nascimento, Comum.string.nascimento_vazio)
        viewModel.mudarNascimento("1990-05-10")
        recusou(CampoSobreVoce.Cpf, R.string.sobre_voce_falta_cpf)
        viewModel.mudarCpf("52998224724")
        recusou(CampoSobreVoce.Cpf, R.string.cadastro_cpf_invalido)
        viewModel.mudarCpf("52998224725")
        assertEquals("529.982.247-25", viewModel.estado.value.cpf)
        recusou(CampoSobreVoce.Telefone, R.string.cadastro_falta_celular)
        assertEquals(emptyList<String>(), respondeCompleto.pedidos)

        viewModel.mudarTelefone("912 345 678")
        viewModel.estado.test {
            viewModel.continuar()
            val salvo = ate { !it.enviando && it.campoComProblema == null }
            assertNull(salvo.erro)
        }
        assertEquals(
            """PUT /api/me/sobre-voce {"apelido":"Ti","nascimento":"1990-05-10","cpf":"52998224725","telefone":"+351 912 345 678"}""",
            respondeCompleto.pedidos.first(),
        )
        assertNull(sessao.usuario!!.pendencia)
    }

    @Test
    fun `erro da API aparece no topo`() = runTest {
        val fixas = RespostasFixas("PUT /api/me/sobre-voce" to RespostasFixas.erro(500, "ERRO_INTERNO", "Tente de novo."))
        val sessao = sessaoDeTeste(pasta.root, fixas)
        sessao.entrou(usuario(""","apelido":"Tita","nascimento":"1990-05-10","cadastroCompleto":false"""))
        val viewModel = SobreVoceViewModel(sessao)
        viewModel.estado.test {
            viewModel.continuar()
            val recusado = ate { !it.enviando && it.erro != null }
            assertNull(recusado.campoComProblema)
            assertEquals(MensagemDeErro.DoServidor("Tente de novo."), recusado.erro)
        }
    }
}
