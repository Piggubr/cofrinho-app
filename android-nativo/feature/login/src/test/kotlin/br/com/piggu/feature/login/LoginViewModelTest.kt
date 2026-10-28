package br.com.piggu.feature.login

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import br.com.piggu.core.data.AutenticacaoDaSessao
import br.com.piggu.core.data.EstadoDaSessao
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.datastore.Cifra
import br.com.piggu.core.datastore.CofreDaSessao
import br.com.piggu.core.model.JsonDoPiggu
import br.com.piggu.core.network.AvisoDeVersao
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ModuloDaRede
import br.com.piggu.core.network.ServicoDeIdentidade
import br.com.piggu.core.network.URL_DA_API
import br.com.piggu.core.testing.ApiDaDemo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private object SemCifra : Cifra {
    override fun cifrar(claro: ByteArray) = claro
    override fun decifrar(cifrado: ByteArray) = cifrado
}

private object SemCache : RespostasGuardadasDao {
    override suspend fun ler(chave: String): RespostaGuardada? = null
    override suspend fun guardar(resposta: RespostaGuardada) = Unit
    override suspend fun apagarTudo() = Unit
}

/** O login com a sessão de verdade, respondida pela API falsa da demonstração. */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule val pasta = TemporaryFolder()

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun devolver() = Dispatchers.resetMain()

    private fun TestScope.sessao(): Sessao {
        val cofre = CofreDaSessao(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { pasta.root.resolve("s.preferences_pb") }), SemCifra)
        lateinit var identidade: ServicoDeIdentidade
        val sessao = Sessao({ identidade }, cofre, SemCache, backgroundScope)
        val cliente = ModuloDaRede.okHttp("2.0.0", AvisoDeVersao(), AutenticacaoDaSessao(sessao), setOf(ApiDaDemo()))
        identidade = ModuloDaRede.identidade(ModuloDaRede.retrofit(URL_DA_API, cliente, JsonDoPiggu))
        return sessao
    }

    @Test
    fun `sem e-mail ou senha pede os dois e nem chama o servidor`() = runTest {
        val viewModel = LoginViewModel(sessao())
        viewModel.mudarEmail("  ")
        viewModel.entrar()
        assertEquals(MensagemDeErro.DoApp(R.string.login_falta_email_e_senha), viewModel.estado.value.erro)
        assertFalse(viewModel.estado.value.entrando)
    }

    @Test
    fun `entrar abre a sessao e limpa a senha`() = runTest {
        val sessao = sessao()
        val viewModel = LoginViewModel(sessao)
        viewModel.mudarEmail("ana@demo.piggu.app")
        viewModel.mudarSenha("qualquer")
        viewModel.entrar()
        // A API falsa responde em outra thread (OkHttp): espera fora do relógio virtual do teste.
        withContext(Dispatchers.Default) { withTimeout(5_000) { viewModel.estado.first { !it.entrando } } }
        assertTrue(sessao.estado.value is EstadoDaSessao.Logado)
        assertEquals("", viewModel.estado.value.senha)
        assertEquals(null, viewModel.estado.value.erro)
    }
}
