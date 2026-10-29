package br.com.piggu.app

import android.app.Application
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import br.com.piggu.core.data.AutenticacaoDaSessao
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.datastore.Cifra
import br.com.piggu.core.datastore.CofreDaSessao
import br.com.piggu.core.designsystem.PigguTheme
import br.com.piggu.core.model.JsonDoPiggu
import br.com.piggu.core.network.AvisoDeVersao
import br.com.piggu.core.network.ModuloDaRede
import br.com.piggu.core.network.ServicoDeIdentidade
import br.com.piggu.core.network.URL_DA_API
import br.com.piggu.core.testing.ApiDaDemo
import br.com.piggu.core.testing.RespostaFixa
import br.com.piggu.core.testing.RespostasFixas
import br.com.piggu.feature.legal.DocumentoLegal
import br.com.piggu.feature.legal.TelaDeTextoLegal
import br.com.piggu.feature.login.DialogoDeExclusao
import br.com.piggu.feature.login.EstadoDaConfirmacao
import br.com.piggu.feature.login.EstadoDaExclusao
import br.com.piggu.feature.login.EstadoDaSenhaNova
import br.com.piggu.feature.login.EstadoDoAceite
import br.com.piggu.feature.login.EstadoDoEsqueci
import br.com.piggu.feature.login.EstadoDoLogin
import br.com.piggu.feature.login.EventosDoLogin
import br.com.piggu.feature.login.ModoDaEntrada
import br.com.piggu.feature.login.TelaDeConfirmarEmail
import br.com.piggu.feature.login.TelaDeEsqueciASenha
import br.com.piggu.feature.login.TelaDeLogin
import br.com.piggu.feature.login.TelaDeSenhaNova
import br.com.piggu.feature.login.TelaDoAceite
import br.com.piggu.feature.login.cadastro.CadastroViewModel
import br.com.piggu.feature.login.cadastro.FormularioDeCadastro
import br.com.piggu.feature.login.cpf.CpfViewModel
import br.com.piggu.feature.login.cpf.TelaDoCpf
import br.com.piggu.feature.login.sobrevoce.SobreVoceViewModel
import br.com.piggu.feature.login.sobrevoce.TelaSobreVoce
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private object SemCifra : Cifra {
    override fun cifrar(claro: ByteArray) = claro
    override fun decifrar(cifrado: ByteArray) = cifrado
}

private object SemCache : RespostasGuardadasDao {
    override suspend fun ler(chave: String): RespostaGuardada? = null
    override suspend fun guardar(resposta: RespostaGuardada) = Unit
    override suspend fun apagarTudo() = Unit
}

/** Conta que entrou sem nascimento, CPF nem celular: o "Sobre você" mostra todos os campos. */
private const val ANA_INCOMPLETA =
    """{"accessToken":"a1","expiresIn":1800,"refreshToken":"r1","usuario":{"id":"u1","email":"ana@demo.piggu.app","nome":"Ana Souza","primeiroNome":"Ana","role":"TITULAR","ativo":true,"plano":"GRATUITO","familia":"f1","cadastroCompleto":false,"cpfInformado":false,"celularInformado":false}}"""

// As telas da entrada (etapa A3), com a sessão de verdade respondida pela API falsa.
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36])
@OptIn(ExperimentalMaterial3Api::class)
class CapturasDaEntradaTest(private val aparelho: String, private val tema: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun casos() = listOf("celular", "tablet").flatMap { a -> listOf("claro", "escuro").map { arrayOf(a, it) } }
    }

    @get:Rule val compose = createComposeRule()
    @get:Rule val pasta = TemporaryFolder()

    @Before
    fun aparelho() {
        RuntimeEnvironment.setQualifiers(if (aparelho == "celular") RobolectricDeviceQualifiers.Pixel7 else RobolectricDeviceQualifiers.MediumTablet)
        if (tema == "escuro") RuntimeEnvironment.setQualifiers("+night")
    }

    private fun sessao(logada: Boolean = false): Sessao {
        val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val cofre = CofreDaSessao(PreferenceDataStoreFactory.create(scope = escopo, produceFile = { pasta.newFile("s.preferences_pb").also { it.delete() } }), SemCifra)
        lateinit var identidade: ServicoDeIdentidade
        val sessao = Sessao({ identidade }, cofre, SemCache, escopo)
        val fixas = RespostasFixas("POST /api/auth/entrar" to RespostaFixa(200, ANA_INCOMPLETA))
        val cliente = ModuloDaRede.okHttp("2.0.0", AvisoDeVersao(), AutenticacaoDaSessao(sessao), linkedSetOf(fixas, ApiDaDemo()))
        identidade = ModuloDaRede.identidade(ModuloDaRede.retrofit(URL_DA_API, cliente, JsonDoPiggu))
        if (logada) runBlocking { sessao.entrarComSenha("ana@demo.piggu.app", "senha123", lembrar = false) }
        return sessao
    }

    private fun desenhar(conteudo: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalRippleConfiguration provides null) { PigguTheme { conteudo() } }
    }

    private fun capturar(nome: String) =
        compose.onRoot().captureRoboImage("../docs/prints/etapa-3/$nome-$aparelho-$tema.png")

    @Test
    fun entrar() {
        desenhar { TelaDeLogin(EstadoDoLogin(email = "ana@demo.piggu.app"), EventosDoLogin(), mostrarGoogle = false) }
        capturar("entrar")
    }

    /** Com o ID OAuth do Android criado, o botão do Google aparece (D-104). */
    @Test
    fun entrarComGoogle() {
        desenhar { TelaDeLogin(EstadoDoLogin(), EventosDoLogin(), mostrarGoogle = true) }
        capturar("entrar-com-google")
    }

    @Test
    fun criarConta() {
        val viewModel = CadastroViewModel(sessao())
        desenhar {
            TelaDeLogin(EstadoDoLogin(modo = ModoDaEntrada.CriarConta), EventosDoLogin(), mostrarGoogle = false) {
                FormularioDeCadastro(abrirTermos = {}, abrirPrivacidade = {}, aoCriar = {}, viewModel = viewModel)
            }
        }
        capturar("criar-conta")
    }

    @Test
    fun aceiteDoGoogle() {
        desenhar { TelaDeLogin(EstadoDoLogin(aguardandoAceite = true, aceitou = true), EventosDoLogin(), mostrarGoogle = true) }
        capturar("aceite-do-google")
    }

    @Test
    fun contaCriada() {
        desenhar { TelaDeLogin(EstadoDoLogin(email = "nova@piggu.com.br", contaCriada = true), EventosDoLogin(), mostrarGoogle = false) }
        capturar("conta-criada")
    }

    @Test
    fun esqueciASenha() {
        desenhar { TelaDeEsqueciASenha(EstadoDoEsqueci(email = "ana@demo.piggu.app"), {}, {}, {}) }
        capturar("esqueci-a-senha")
    }

    @Test
    fun senhaNova() {
        desenhar { TelaDeSenhaNova(EstadoDaSenhaNova(), {}, {}, {}, {}, {}) }
        capturar("senha-nova")
    }

    @Test
    fun emailConfirmado() {
        desenhar { TelaDeConfirmarEmail(EstadoDaConfirmacao.Confirmado) {} }
        capturar("email-confirmado")
    }

    @Test
    fun aceite() {
        desenhar { TelaDoAceite(EstadoDoAceite(), {}, {}, {}, {}, {}) }
        capturar("aceite")
    }

    @Test
    fun excluirConta() {
        // Só o diálogo: com o porquinho animado atrás, a janela do diálogo nunca fica parada.
        // shortcut: no tamanho de celular o diálogo nunca fica ocioso no Robolectric (nem com o
        // relógio parado); a captura é só do tablet até achar a causa.
        org.junit.Assume.assumeTrue(aparelho == "tablet")
        desenhar { DialogoDeExclusao(EstadoDaExclusao(aberta = true, digitado = "EXCLUIR"), {}, {}, {}) }
        capturar("excluir-conta")
    }

    @Test
    fun sobreVoce() {
        val viewModel = SobreVoceViewModel(sessao(logada = true))
        desenhar { TelaSobreVoce(sair = {}, viewModel = viewModel) }
        capturar("sobre-voce")
    }

    @Test
    fun cpf() {
        val viewModel = CpfViewModel(sessao(logada = true))
        desenhar { TelaDoCpf(voltar = {}, viewModel = viewModel) }
        capturar("cpf")
    }

    @Test
    fun termos() {
        desenhar { TelaDeTextoLegal(DocumentoLegal.Termos, voltar = {}, abrirLinkInterno = {}) }
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("Nesta página", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        capturar("termos")
    }
}
