package br.com.piggu.feature.login.cpf
import br.com.piggu.core.testing.sessaoDeTeste
import br.com.piggu.core.testing.esperar

import app.cash.turbine.test
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.testing.RespostasFixas
import br.com.piggu.feature.login.R
import br.com.piggu.feature.login.cadastro.ate
import br.com.piggu.feature.login.cadastro.usuario
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** cpf.spec.ts, com a sessão de verdade respondida pela API falsa. */
@OptIn(ExperimentalCoroutinesApi::class)
class CpfViewModelTest {
    @get:Rule val pasta = TemporaryFolder()

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun devolver() = Dispatchers.resetMain()

    @Test
    fun `CPF vazio ou invalido avisa no campo e nem chama o servidor`() = runTest {
        val fixas = RespostasFixas()
        val viewModel = CpfViewModel(sessaoDeTeste(pasta.root, fixas))
        viewModel.continuar()
        assertEquals(MensagemDeErro.DoApp(R.string.cadastro_falta_documento), viewModel.estado.value.erroNoCampo)
        viewModel.mudarDocumento("529.982.247-26")
        assertNull(viewModel.estado.value.erroNoCampo)
        viewModel.continuar()
        assertEquals(MensagemDeErro.DoApp(R.string.cadastro_cpf_invalido), viewModel.estado.value.erroNoCampo)
        assertEquals(2, viewModel.estado.value.tentativas)
        assertFalse(viewModel.estado.value.gravado)
        assertEquals(emptyList<String>(), fixas.pedidos)
    }

    @Test
    fun `grava o pais e o CPF e volta`() = runTest {
        val fixas = RespostasFixas()
        val sessao = sessaoDeTeste(pasta.root, fixas)
        sessao.entrou(usuario(""","pais":"PT","cpfInformado":false"""))
        val viewModel = CpfViewModel(sessao)
        assertEquals("PT", viewModel.estado.value.pais)
        viewModel.mudarPais("US")
        viewModel.mudarDocumento("52998224725")
        viewModel.estado.test {
            viewModel.continuar()
            val gravado = ate { it.gravado }
            assertNull(gravado.erro)
        }
        assertEquals("""PUT /api/me/pais {"pais":"US","documento":"52998224725"}""", fixas.pedidos.first())
        assertEquals(true, sessao.usuario!!.cpfInformado)
    }
}
