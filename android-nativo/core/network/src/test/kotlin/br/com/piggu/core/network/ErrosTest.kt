package br.com.piggu.core.network

import br.com.piggu.core.model.CampoInvalido
import br.com.piggu.core.model.PedidoDeEmail
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** O corpo de erro da API (API_SPEC, Erros) e os casos do mensagem-de-erro.spec.ts do site. */
class ErrosTest {
    private val api = ApiDeTeste()

    @After
    fun fechar() = api.close()

    private suspend fun erroDe(chamar: suspend () -> Unit): Throwable {
        try {
            chamar()
        } catch (erro: Throwable) {
            return erro
        }
        fail("a chamada devia falhar")
        error("")
    }

    @Test
    fun `usa a mensagem que o backend escreveu`() = runTest {
        api.responder(
            422,
            """{"erro":"O saldo não pode ficar negativo.","codigo":"BusinessException","campos":[],"momento":"2026-10-25T23:09:08Z"}""",
            "Content-Type", "application/json", "X-Request-Id", "abc-123",
        )

        val erro = erroDe { api.financas.consultarCofrinho() } as ErroDaApi.DoServidor

        assertEquals(422, erro.status)
        assertEquals("BusinessException", erro.codigo)
        assertEquals("abc-123", erro.requestId)
        assertEquals(MensagemDeErro.DoServidor("O saldo não pode ficar negativo."), mensagemDeErro(erro))
    }

    @Test
    fun `validacao traz o erro de cada campo`() = runTest {
        api.responder(
            400,
            """{"erro":"Confira o e-mail.","codigo":"VALIDACAO","campos":[{"campo":"email","mensagem":"Confira o e-mail."}]}""",
        )

        val erro = erroDe { api.identidade.convidar(PedidoDeEmail("x")) } as ErroDaApi.DoServidor

        assertEquals("VALIDACAO", erro.codigo)
        assertEquals(listOf(CampoInvalido("email", "Confira o e-mail.")), erro.campos)
    }

    @Test
    fun `avisa sobre conexao quando nao houve resposta`() = runTest {
        api.servidor.close()

        val erro = erroDe { api.identidade.meuPerfil() }

        assertTrue(erro is ErroDaApi.SemInternet)
        assertEquals(MensagemDeErro.DoApp(R.string.erro_sem_conexao), mensagemDeErro(erro))
    }

    @Test
    fun `aceita corpo em texto puro`() = runTest {
        api.responder(500, "Falha bruta", "Content-Type", "text/plain")

        val erro = erroDe { api.identidade.meuPerfil() }

        assertEquals(MensagemDeErro.DoServidor("Falha bruta"), mensagemDeErro(erro))
    }

    @Test
    fun `cai no padrao quando o corpo nao traz mensagem`() = runTest {
        api.responder(500, """{"algo":"inesperado"}""")
        val semMensagem = erroDe { api.identidade.meuPerfil() }
        api.responder(502, "<html><body>Bad Gateway</body></html>", "Content-Type", "text/html")
        val paginaDoProxy = erroDe { api.identidade.meuPerfil() }
        api.responder(401)
        val semCorpo = erroDe { api.identidade.meuPerfil() }

        for (erro in listOf(semMensagem, paginaDoProxy, semCorpo)) {
            assertEquals(MensagemDeErro.DoApp(android.R.string.cancel), mensagemDeErro(erro, android.R.string.cancel))
        }
        assertEquals(401, (semCorpo as ErroDaApi.DoServidor).status)
    }

    @Test
    fun `cai no padrao quando nem e um erro HTTP`() {
        assertEquals(
            MensagemDeErro.DoApp(R.string.erro_nao_foi_possivel_concluir),
            mensagemDeErro(IllegalStateException("interno")),
        )
    }

    @Test
    fun `resposta fora do contrato vira resposta invalida`() = runTest {
        api.responder(200, """{"id":42}""")

        assertTrue(erroDe { api.identidade.meuPerfil() } is ErroDaApi.RespostaInvalida)
    }

    @Test
    fun `426 com ATUALIZE_O_APP liga o aviso de versao`() = runTest {
        api.responder(426, """{"erro":"Atualize o app.","codigo":"OUTRA_COISA"}""")
        erroDe { api.identidade.meuPerfil() }
        assertFalse(api.aviso.precisaAtualizar.value)

        api.responder(426, """{"erro":"Atualize o app.","codigo":"ATUALIZE_O_APP"}""")
        val erro = erroDe { api.identidade.meuPerfil() } as ErroDaApi.DoServidor

        assertTrue(api.aviso.precisaAtualizar.value)
        assertEquals("ATUALIZE_O_APP", erro.codigo)
    }

    @Test
    fun `historico junta dinheiro e pessoas, do mais novo ao mais velho, ate o limite`() = runTest {
        api.servidor.dispatcher = rotas(
            "/api/history" to """[{"id":1,"acao":"CRIOU","entidade":"gasto","quando":"2026-10-01T10:00:00Z"},
                                  {"id":2,"acao":"PAGOU","entidade":"conta","quando":"2026-10-03T10:00:00Z"}]""",
            "/api/family/history" to """[{"id":3,"acao":"ENTROU","entidade":"membro","quando":"2026-10-02T10:00:00Z"},
                                         {"id":4,"acao":"SAIU","entidade":"membro","quando":"2026-09-01T10:00:00Z"}]""",
        )

        val historico = historicoDaFamilia(api.identidade, api.financas, limite = 3)

        assertEquals(listOf(2L, 3L, 1L), historico.map { it.id })
    }

    @Test
    fun `um servico fora do ar nao esconde o que o outro tem`() = runTest {
        api.servidor.dispatcher = rotas(
            "/api/family/history" to """[{"id":9,"acao":"ENTROU","entidade":"membro","quando":"2026-10-01T00:00:00Z"}]""",
        )

        assertEquals(listOf(9L), historicoDaFamilia(api.identidade, api.financas).map { it.id })
    }

    private fun rotas(vararg respostas: Pair<String, String>) = object : mockwebserver3.Dispatcher() {
        override fun dispatch(request: mockwebserver3.RecordedRequest) =
            respostas.toMap()[request.url.encodedPath]
                ?.let { mockwebserver3.MockResponse(200, okhttp3.Headers.headersOf(), it) }
                ?: mockwebserver3.MockResponse(503, okhttp3.Headers.headersOf(), "")
    }

    @Test
    fun `erro do app sai sem e-mail, CPF, telefone, token, busca nem numero comprido`() {
        assertEquals(
            "falhou para [email], CPF [cpf]",
            semDadosPessoais("falhou para ana@piggu.test, CPF 123.456.789-09"),
        )
        assertEquals(
            "[telefone] [token] /x?[...] [numero]",
            semDadosPessoais("(11) 98765-4321 eyJa.eyJb.c /x?q=mercado 4111111111111111"),
        )
    }

    @Test
    fun `acha e-mail colado em outro, entre arrobas soltas e sem o ponto do fim da frase`() {
        assertEquals(
            "\"[email]\" [email][email] x@[email] [email].",
            semDadosPessoais("\"ana.b+1@piggu.test\" a+b@c.d+e@f.g x@y@z.com fim@piggu.com."),
        )
        assertEquals("ana@piggu. @piggu.com ana@ a@b", semDadosPessoais("ana@piggu. @piggu.com ana@ a@b"))
    }

    @Test(timeout = 500)
    fun `texto comprido sem e-mail passa rapido`() {
        semDadosPessoais("a".repeat(50_000) + "@".repeat(50_000))
    }
}
