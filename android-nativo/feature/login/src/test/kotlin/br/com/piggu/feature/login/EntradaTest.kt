package br.com.piggu.feature.login
import br.com.piggu.core.testing.sessaoDeTeste
import br.com.piggu.core.testing.esperar

import app.cash.turbine.test
import br.com.piggu.core.data.EstadoDaSessao
import br.com.piggu.core.data.Pendencia
import br.com.piggu.core.data.VERSAO_DOS_TERMOS
import br.com.piggu.core.data.pendencia
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.testing.RespostaFixa
import br.com.piggu.core.testing.RespostasFixas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

private fun usuario(extra: String = "") =
    """{"id":"u1","email":"ana@piggu.com.br","nome":"Ana Souza","primeiroNome":"Ana","role":"TITULAR","ativo":true,"plano":"GRATUITO","familia":"f1"$extra}"""

private fun tokens(extra: String = "") =
    RespostaFixa(200, """{"accessToken":"a1","expiresIn":1800,"refreshToken":"r1","usuario":${usuario(extra)}}""")

/** Cada caminho da entrada, com a sessão de verdade e a API falsa (core:testing). */
@OptIn(ExperimentalCoroutinesApi::class)
class EntradaTest {
    @get:Rule val pasta = TemporaryFolder()

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())
    // Sem resetMain: resposta atrasada da API falsa (outra thread) iria para um Main desfeito e derrubava o teste seguinte no CI.
    private fun LoginViewModel.preencher(email: String = "ana@piggu.com.br", senha: String = "senha123") {
        mudarEmail(email)
        mudarSenha(senha)
    }

    @Test
    fun `senha errada mostra a mensagem do servidor e nao entra`() = runTest {
        val fixas = RespostasFixas("POST /api/auth/entrar" to RespostasFixas.erro(401, "CREDENCIAIS_INVALIDAS", "E-mail ou senha incorretos."))
        val sessao = sessaoDeTeste(pasta.root, fixas)
        val viewModel = LoginViewModel(sessao)
        viewModel.preencher(senha = "errada1")
        viewModel.estado.test {
            awaitItem()
            viewModel.entrar()
            assertTrue(awaitItem().entrando)
            val depois = awaitItem()
            assertFalse(depois.entrando)
            assertEquals(MensagemDeErro.DoServidor("E-mail ou senha incorretos."), depois.erro)
            assertNull(depois.senhaParaSalvar)
        }
        assertEquals(EstadoDaSessao.Verificando, sessao.estado.value)
    }

    @Test
    fun `entrou com a senha e oferece guardar no gerenciador de senhas`() = runTest {
        val viewModel = LoginViewModel(sessaoDeTeste(pasta.root))
        viewModel.preencher(email = " ana@demo.piggu.app ")
        viewModel.entrar()
        val estado = viewModel.estado.esperar { !it.entrando }
        assertEquals("ana@demo.piggu.app" to "senha123", estado.senhaParaSalvar)
        assertEquals("", estado.senha)
        viewModel.senhaOferecida()
        assertNull(viewModel.estado.value.senhaParaSalvar)
    }

    @Test
    fun `e-mail nao confirmado entra, e o aviso de confirmar aparece pelo perfil`() = runTest {
        val sessao = sessaoDeTeste(pasta.root, RespostasFixas("POST /api/auth/entrar" to tokens(""","emailConfirmado":false""")))
        val viewModel = LoginViewModel(sessao)
        viewModel.preencher()
        viewModel.entrar()
        val logado = sessao.estado.esperar { it is EstadoDaSessao.Logado } as EstadoDaSessao.Logado
        assertEquals(false, logado.usuario.emailConfirmado)
        assertNull(viewModel.estado.value.erro)
    }

    @Test
    fun `termos pendentes levam ao aceite, e aceitar libera o app`() = runTest {
        val fixas = RespostasFixas(
            "POST /api/auth/entrar" to tokens(""","termosAceitos":"2025-01-01""""),
            "PUT /api/me/termos" to RespostaFixa(200, usuario(""","termosAceitos":"$VERSAO_DOS_TERMOS"""")),
        )
        val sessao = sessaoDeTeste(pasta.root, fixas)
        LoginViewModel(sessao).apply { preencher(); entrar() }
        val logado = sessao.estado.esperar { it is EstadoDaSessao.Logado } as EstadoDaSessao.Logado
        assertEquals(Pendencia.Termos, logado.usuario.pendencia)

        val aceite = AceiteViewModel(sessao, br.com.piggu.core.testing.FilaNaMemoria())
        aceite.aceitar()
        aceite.estado.esperar { !it.salvando }
        assertNull((sessao.estado.value as EstadoDaSessao.Logado).usuario.pendencia)
        assertTrue(fixas.pedidos.any { it == """PUT /api/me/termos {"versao":"$VERSAO_DOS_TERMOS"}""" })
    }

    @Test
    fun `cadastro incompleto leva ao Sobre voce`() = runTest {
        val sessao = sessaoDeTeste(pasta.root, RespostasFixas("POST /api/auth/entrar" to tokens(""","cadastroCompleto":false""")))
        LoginViewModel(sessao).apply { preencher(); entrar() }
        val logado = sessao.estado.esperar { it is EstadoDaSessao.Logado } as EstadoDaSessao.Logado
        assertEquals(Pendencia.SobreVoce, logado.usuario.pendencia)
    }

    @Test
    fun `Google cancelado nao mostra erro nem chama o servidor`() = runTest {
        val fixas = RespostasFixas()
        val viewModel = LoginViewModel(sessaoDeTeste(pasta.root, fixas))
        viewModel.aoVoltarDoGoogle(ResultadoDoGoogle.Cancelado)
        assertNull(viewModel.estado.value.erro)
        assertFalse(viewModel.estado.value.entrando)
        assertTrue(fixas.pedidos.isEmpty())
    }

    @Test
    fun `Google que falha pede e-mail e senha`() = runTest {
        val viewModel = LoginViewModel(sessaoDeTeste(pasta.root))
        viewModel.aoVoltarDoGoogle(ResultadoDoGoogle.Falhou)
        assertEquals(MensagemDeErro.DoApp(R.string.login_google_falhou), viewModel.estado.value.erro)
    }

    @Test
    fun `conta nova pelo Google pede o aceite e cria com a versao dos termos`() = runTest {
        val fixas = RespostasFixas(
            "POST /api/auth/google" to RespostasFixas.erro(400, "TERMOS_NECESSARIOS", "Aceite os termos para criar a conta."),
        )
        val sessao = sessaoDeTeste(pasta.root, fixas)
        val viewModel = LoginViewModel(sessao)
        viewModel.aoVoltarDoGoogle(ResultadoDoGoogle.Token("id-do-google"))
        val aguardando = viewModel.estado.esperar { !it.entrando }
        assertTrue(aguardando.aguardandoAceite)
        assertNull(aguardando.erro)

        // Sem marcar o aceite, nada sai.
        viewModel.criarContaComGoogle()
        assertEquals(1, fixas.pedidos.count { it.startsWith("POST /api/auth/google") })

        viewModel.marcarAceite(true)
        viewModel.criarContaComGoogle()
        viewModel.estado.esperar { !it.entrando }
        val segundo = fixas.pedidos.last { it.startsWith("POST /api/auth/google") }
        assertTrue(segundo.contains(""""idToken":"id-do-google"""") && segundo.contains(""""versaoDosTermos":"$VERSAO_DOS_TERMOS""""))
        assertTrue(segundo.contains(""""refreshNoCorpo":true"""))
    }

    @Test
    fun `conta criada volta para entrar com o e-mail e o aviso`() = runTest {
        val viewModel = LoginViewModel(sessaoDeTeste(pasta.root))
        viewModel.trocarModo(ModoDaEntrada.CriarConta)
        viewModel.contaCriada("nova@piggu.com.br")
        val estado = viewModel.estado.value
        assertEquals(ModoDaEntrada.Entrar, estado.modo)
        assertEquals("nova@piggu.com.br", estado.email)
        assertTrue(estado.contaCriada)
    }

    @Test
    fun `esqueci a senha confere o e-mail e responde igual com ou sem conta`() = runTest {
        val viewModel = EsqueciSenhaViewModel(sessaoDeTeste(pasta.root))
        viewModel.mudarEmail("sem-arroba")
        viewModel.pedir()
        assertEquals(MensagemDeErro.DoApp(R.string.esqueci_confira_o_email), viewModel.estado.value.erro)
        viewModel.mudarEmail("qualquer@piggu.com.br")
        viewModel.pedir()
        assertTrue(viewModel.estado.esperar { !it.enviando }.enviado)
    }

    @Test
    fun `senha nova com link vencido pede outro link`() = runTest {
        val fixas = RespostasFixas("POST /api/auth/senha/redefinir" to RespostasFixas.erro(400, "LINK_INVALIDO", "Link inválido."))
        val viewModel = SenhaNovaViewModel(sessaoDeTeste(pasta.root, fixas), "token-velho")
        viewModel.mudarSenha("novaSenha1")
        viewModel.mudarConfirmacao("outraSenha1")
        viewModel.salvar()
        assertEquals(MensagemDeErro.DoApp(R.string.redefinir_diferentes), viewModel.estado.value.erro)
        viewModel.mudarConfirmacao("novaSenha1")
        viewModel.salvar()
        assertEquals(EtapaDaSenhaNova.LinkRuim, viewModel.estado.esperar { !it.salvando }.etapa)
    }

    @Test
    fun `senha nova salva derruba a sessao deste aparelho`() = runTest {
        val sessao = sessaoDeTeste(pasta.root)
        LoginViewModel(sessao).apply { preencher(email = "ana@demo.piggu.app"); entrar() }
        sessao.estado.esperar { it is EstadoDaSessao.Logado }
        val viewModel = SenhaNovaViewModel(sessao, "token-bom")
        viewModel.mudarSenha("novaSenha1")
        viewModel.mudarConfirmacao("novaSenha1")
        viewModel.salvar()
        assertEquals(EtapaDaSenhaNova.Salva, viewModel.estado.esperar { !it.salvando }.etapa)
        assertEquals(EstadoDaSessao.Deslogado, sessao.estado.value)
    }

    @Test
    fun `link sem token ja abre como vencido`() = runTest {
        assertEquals(EtapaDaSenhaNova.LinkRuim, SenhaNovaViewModel(sessaoDeTeste(pasta.root), "").estado.value.etapa)
    }

    @Test
    fun `confirmar e-mail mostra o erro do link`() = runTest {
        val fixas = RespostasFixas("POST /api/auth/confirmar-email" to RespostasFixas.erro(400, "LINK_INVALIDO", "Este link venceu."))
        val viewModel = ConfirmarEmailViewModel(sessaoDeTeste(pasta.root, fixas), "t1")
        assertEquals(EstadoDaConfirmacao.Falhou(MensagemDeErro.DoServidor("Este link venceu.")), viewModel.estado.esperar { it != EstadoDaConfirmacao.Confirmando })
    }

    @Test
    fun `confirmar e-mail com o link bom`() = runTest {
        val fixas = RespostasFixas("POST /api/auth/confirmar-email" to RespostaFixa(204))
        val viewModel = ConfirmarEmailViewModel(sessaoDeTeste(pasta.root, fixas), "t1")
        assertEquals(EstadoDaConfirmacao.Confirmado, viewModel.estado.esperar { it != EstadoDaConfirmacao.Confirmando })
        assertTrue(fixas.pedidos.contains("""POST /api/auth/confirmar-email {"token":"t1"}"""))
    }

    @Test
    fun `reenviar a confirmacao`() = runTest {
        val fixas = RespostasFixas("POST /api/auth/me/email/reenviar" to RespostaFixa(204))
        val viewModel = ReenvioViewModel(sessaoDeTeste(pasta.root, fixas))
        viewModel.reenviar()
        assertTrue(viewModel.estado.esperar { !it.enviando }.enviado)
    }

    @Test
    fun `excluir a conta so com a palavra EXCLUIR, e depois a sessao acaba`() = runTest {
        // A demonstração não apaga a conta (como a do site): aqui o servidor aceita.
        val sessao = sessaoDeTeste(pasta.root, RespostasFixas("DELETE /api/me" to RespostaFixa(204)))
        LoginViewModel(sessao).apply { preencher(email = "ana@demo.piggu.app"); entrar() }
        sessao.estado.esperar { it is EstadoDaSessao.Logado }
        val viewModel = ExclusaoViewModel(sessao)
        viewModel.abrir()
        viewModel.digitar("excluir quase")
        viewModel.excluir()
        assertFalse(viewModel.estado.value.excluindo)
        viewModel.digitar("  excluir ")
        viewModel.excluir()
        sessao.estado.esperar { it == EstadoDaSessao.Deslogado }
        assertFalse(viewModel.estado.esperar { !it.excluindo }.aberta)
    }
}
