package br.com.piggu.core.network

import br.com.piggu.core.model.Avisos
import br.com.piggu.core.model.EdicaoDeGasto
import br.com.piggu.core.model.FilmeDoCatalogo
import br.com.piggu.core.model.JsonDoPiggu
import br.com.piggu.core.model.LinhaParaImportar
import br.com.piggu.core.model.ModuloDeEstiloDeVida
import br.com.piggu.core.model.NovaConta
import br.com.piggu.core.model.NovaFotoDoFeed
import br.com.piggu.core.model.NovoDeposito
import br.com.piggu.core.model.NovoPremio
import br.com.piggu.core.model.PedidoComToken
import br.com.piggu.core.model.PedidoDeAlteracaoDeUsuario
import br.com.piggu.core.model.PedidoDeCheckout
import br.com.piggu.core.model.PedidoDeCompartilhamento
import br.com.piggu.core.model.PedidoDeConexao
import br.com.piggu.core.model.PedidoDeImportacao
import br.com.piggu.core.model.PedidoDeLeitura
import br.com.piggu.core.model.PedidoDeLogin
import br.com.piggu.core.model.PedidoDeModulos
import br.com.piggu.core.model.PedidoDePapel
import br.com.piggu.core.model.PedidoDeRefresh
import br.com.piggu.core.model.PedidoDeSenha
import br.com.piggu.core.model.PedidoDoGoogle
import br.com.piggu.core.model.Periodo
import br.com.piggu.core.model.PigguRole
import br.com.piggu.core.model.TipoDeConta
import br.com.piggu.core.model.Visao
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * O contrato com o gateway: método, rota, query e corpo de cada chamada, como o
 * contrato-da-api.spec.ts do site. Rota trocada aqui quebra a tela em produção sem erro de
 * compilação.
 */
class RotasTest {
    private val api = ApiDeTeste()

    @After
    fun fechar() = api.close()

    private class Caso(
        val metodo: String,
        val caminho: String,
        val query: Map<String, String> = emptyMap(),
        val corpo: String? = null,
        val chamar: suspend ApiDeTeste.() -> Unit,
    )

    private val casos = listOf(
        // Entrar e manter a sessão (API_SPEC): o refresh vai e volta no corpo.
        Caso(
            "POST", "/api/auth/entrar",
            corpo = """{"email":"ana@x.com","senha":"segredo123","lembrar":true,"refreshNoCorpo":true}""",
        ) { identidade.entrarComSenha(PedidoDeLogin("ana@x.com", "segredo123", lembrar = true)) },
        Caso(
            "POST", "/api/auth/google",
            corpo = """{"idToken":"t","lembrar":false,"refreshNoCorpo":true}""",
        ) { identidade.entrarComGoogle(PedidoDoGoogle("t", lembrar = false)) },
        Caso("POST", "/api/auth/refresh", corpo = """{"refreshToken":"r1"}""") {
            identidade.renovar(PedidoDeRefresh("r1"))
        },
        Caso("POST", "/api/auth/logout", corpo = """{"refreshToken":"r1"}""") {
            identidade.sair(PedidoDeRefresh("r1"))
        },
        Caso("PUT", "/api/auth/me/senha", corpo = """{"novaSenha":"nova12345","refreshToken":"r1"}""") {
            identidade.definirSenha(PedidoDeSenha(novaSenha = "nova12345", refreshToken = "r1"))
        },
        Caso("GET", "/api/auth/me") { identidade.meuPerfil() },
        Caso("POST", "/api/auth/confirmar-email", corpo = """{"token":"k"}""") {
            identidade.confirmarEmail(PedidoComToken("k"))
        },
        Caso("POST", "/api/auth/me/email/reenviar", corpo = "{}") { identidade.reenviarConfirmacao() },
        // A conta.
        Caso("PUT", "/api/auth/me/alerts", corpo = """{"email":true,"push":false}""") {
            identidade.salvarAvisos(Avisos(email = true, push = false))
        },
        Caso(
            "PUT", "/api/auth/me/sharing",
            corpo = """{"mostrarTudoAoParceiro":true,"compartilharPorPadrao":false}""",
        ) { identidade.salvarCompartilhamento(PedidoDeCompartilhamento(true, false)) },
        Caso("PUT", "/api/auth/me/modules", corpo = """{"modulos":["filmes","fotos"]}""") {
            identidade.salvarModulos(PedidoDeModulos(listOf(ModuloDeEstiloDeVida.FILMES, ModuloDeEstiloDeVida.FOTOS)))
        },
        Caso("DELETE", "/api/me") { identidade.excluirConta() },
        Caso("GET", "/api/users") { identidade.listarUsuarios() },
        Caso("PATCH", "/api/users/u1", corpo = """{"role":"MEMBRO"}""") {
            identidade.alterarUsuario("u1", PedidoDeAlteracaoDeUsuario(role = PigguRole.MEMBRO))
        },
        // Família, Premium e histórico das pessoas.
        Caso("GET", "/api/family") { identidade.verFamilia() },
        Caso("PUT", "/api/family/members/m1/role", corpo = """{"papel":"PARCEIRO"}""") {
            identidade.mudarPapel("m1", PedidoDePapel(PigguRole.PARCEIRO))
        },
        Caso("POST", "/api/family/invites/v1/accept", corpo = "{}") { identidade.aceitarConvite("v1") },
        Caso("GET", "/api/family/history", mapOf("limite" to "50")) { identidade.historicoDasPessoas() },
        Caso("GET", "/api/billing/plan") { identidade.plano() },
        Caso("POST", "/api/billing/checkout", corpo = """{"periodo":"ANUAL"}""") {
            identidade.checkout(PedidoDeCheckout(Periodo.ANUAL))
        },
        Caso("POST", "/api/billing/refund", corpo = "{}") { identidade.reembolso() },
        // Finanças.
        Caso("GET", "/api/expenses", mapOf("mes" to "2026-10")) { financas.listarGastos("2026-10") },
        Caso("GET", "/api/expenses", mapOf("mes" to "2026-10", "visao" to "casal")) {
            financas.listarGastos("2026-10", Visao.CASAL.parametro)
        },
        Caso("GET", "/api/expenses", mapOf("mes" to "2026-10")) {
            financas.listarGastos("2026-10", Visao.TUDO.parametro)
        },
        Caso("PUT", "/api/expenses/g1", corpo = """{"item":"Pão","categoria":"Mercado","valor":5}""") {
            financas.editarGasto("g1", EdicaoDeGasto("Pão", "Mercado", BigDecimal("5")))
        },
        Caso("DELETE", "/api/expenses/g1") { financas.excluirGasto("g1") },
        Caso("DELETE", "/api/expenses/g1", mapOf("proximas" to "true")) { financas.excluirGasto("g1", proximas = true) },
        Caso(
            "POST", "/api/expenses/import",
            corpo = """{"linhas":[{"data":"2026-10-01","descricao":"Pão","valor":5,"idExterno":"x1","categoria":"Mercado"}],"contaId":"c1"}""",
        ) {
            financas.importarExtrato(
                PedidoDeImportacao(listOf(LinhaParaImportar("2026-10-01", "Pão", BigDecimal("5"), "x1", "Mercado")), "c1"),
            )
        },
        Caso("GET", "/api/products/price-check", mapOf("item" to "Pão", "valor" to "5.50")) {
            financas.conferirPreco("Pão", BigDecimal("5.50").toPlainString())
        },
        Caso("POST", "/api/piggy-bank/deposits", corpo = """{"valor":50,"data":"2026-10-01","objetivo":"o1"}""") {
            financas.depositar(NovoDeposito(BigDecimal("50"), "2026-10-01", "o1"))
        },
        Caso("GET", "/api/bills", mapOf("mes" to "2026-10", "tipo" to "RECEITA")) { financas.receitasRecorrentes("2026-10") },
        Caso("POST", "/api/bills/b1/pay", mapOf("mes" to "2026-10"), corpo = "{}") { financas.pagarContaFixa("b1", "2026-10") },
        Caso("POST", "/api/accounts", corpo = """{"nome":"Nu","tipo":"CARTAO","fechamento":3,"vencimento":10}""") {
            financas.criarConta(NovaConta("Nu", TipoDeConta.CARTAO, 3, 10))
        },
        Caso("PUT", "/api/accounts/c1/statement/paid", mapOf("mes" to "2026-10"), corpo = "") {
            financas.pagarFatura("c1", "2026-10")
        },
        Caso("GET", "/api/reports/year", mapOf("ano" to "2026")) { financas.relatorioDoAno(2026) },
        Caso(
            "POST", "/api/receipts/parse",
            corpo = """{"imageBase64":"b64","mimeType":"image/png","autorizoIa":true,"versaoDoAviso":"v1"}""",
        ) { financas.lerRecibo(PedidoDeLeitura("b64", "image/png", true, "v1")) },
        Caso("GET", "/api/history", mapOf("limite" to "3")) { financas.historicoDoDinheiro(3) },
        // Bancos, estilo de vida, mídia e recompensas.
        Caso("POST", "/api/banking/connect-token", corpo = """{"versaoDoAviso":"v2","autorizo":true}""") {
            bancos.gerarConnectToken(PedidoDeConexao("v2"))
        },
        Caso("DELETE", "/api/banking/connections/x1") { bancos.desconectar("x1") },
        Caso("POST", "/api/banking/sync", corpo = "{}") { bancos.sincronizar() },
        Caso("GET", "/api/movies/random") { estiloDeVida.sortearFilme() },
        Caso("POST", "/api/movies", corpo = """{"tmdbId":"1","titulo":"Up"}""") {
            estiloDeVida.adicionarFilme(FilmeDoCatalogo("1", "Up"))
        },
        Caso("PATCH", "/api/movies/f1/watched", mapOf("assistido" to "true"), corpo = "") {
            estiloDeVida.marcarFilmeAssistido("f1", true)
        },
        Caso("GET", "/api/shopping/items", mapOf("lista" to "Desejos")) { estiloDeVida.listarCompras("Desejos") },
        Caso("POST", "/api/feed", corpo = """{"mesKey":"2026-10","imageBase64":"b64","mimeType":"image/jpeg"}""") {
            midia.publicarNoFeed(NovaFotoDoFeed("2026-10", "b64", "image/jpeg"))
        },
        Caso("GET", "/api/assets/a1/content") { midia.baixarArquivo("a1") },
        Caso("GET", "/api/prizes", mapOf("todos" to "false")) { recompensas.listarPremios() },
        Caso("POST", "/api/prizes", corpo = """{"nome":"Pizza","preco":50}""") {
            recompensas.criarPremio(NovoPremio("Pizza", preco = 50))
        },
        Caso("POST", "/api/prizes/p1/redemptions", corpo = "{}") { recompensas.resgatar("p1") },
    )

    private fun json(texto: String): JsonElement = JsonDoPiggu.parseToJsonElement(texto)

    @Test
    fun `cada chamada vai com o metodo, a rota, a query e o corpo do site`() = runTest {
        for (caso in casos) {
            api.responder(204)
            // A resposta não importa aqui: só o pedido.
            runCatching { caso.chamar(api) }
            val pedido = api.pedido()
            val nome = "${caso.metodo} ${caso.caminho}"

            assertEquals(nome, caso.metodo, pedido.method)
            assertEquals(nome, caso.caminho, pedido.url.encodedPath)
            assertEquals(nome, caso.query, pedido.url.queryParameterNames.associateWith { pedido.url.queryParameter(it) })
            val corpo = pedido.body?.utf8().orEmpty()
            if (caso.corpo.isNullOrEmpty()) {
                assertEquals(nome, "", corpo)
            } else {
                assertEquals(nome, json(caso.corpo), json(corpo))
                assertEquals(nome, "application/json; charset=utf-8", pedido.headers["Content-Type"])
            }
        }
    }

    @Test
    fun `manda a versao do app no User-Agent`() = runTest {
        api.responder(204)
        runCatching { api.identidade.meuPerfil() }

        assertEquals("Piggu-Android/2.0.0", api.pedido().headers["User-Agent"])
    }

    @Test
    fun `a imagem vem direto do endereco do arquivo`() {
        assertEquals("https://piggu.com.br/api/assets/a1/content", urlDaImagem(URL_DA_API, "a1"))
    }
}
