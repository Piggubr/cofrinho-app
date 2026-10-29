package br.com.piggu.core.data

import br.com.piggu.core.model.JsonDoPiggu
import br.com.piggu.core.model.PedidoDeLogin
import br.com.piggu.core.network.AutenticacaoDaRede
import br.com.piggu.core.network.AvisoDeVersao
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.ModuloDaRede
import br.com.piggu.core.network.URL_DA_API
import br.com.piggu.core.testing.ApiDaDemo
import kotlinx.coroutines.test.runTest
import okhttp3.Authenticator
import okhttp3.Interceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A API falsa da demonstração (portada de api-da-demo.ts) lida pelos modelos do app (portados de
 * models.ts e dos yaml): toda rota de leitura que o app usa tem que virar data class sem erro.
 */
class DemoContraModelosTest {
    private val zona = ZoneId.of("America/Sao_Paulo")
    private val dia = Clock.fixed(ZonedDateTime.of(2026, 10, 28, 10, 0, 0, 0, zona).toInstant(), zona)

    private val semSessao = object : AutenticacaoDaRede {
        override val interceptador = Interceptor { it.proceed(it.request()) }
        override val autenticador = Authenticator.NONE
    }
    private val retrofit = ModuloDaRede.retrofit(
        URL_DA_API,
        ModuloDaRede.okHttp("2.0.0", AvisoDeVersao(), semSessao, setOf(ApiDaDemo(dia))),
        JsonDoPiggu,
    )
    private val identidade = ModuloDaRede.identidade(retrofit)
    private val financas = ModuloDaRede.financas(retrofit)
    private val bancos = ModuloDaRede.bancos(retrofit)
    private val estilo = ModuloDaRede.estiloDeVida(retrofit)
    private val midia = ModuloDaRede.midia(retrofit)
    private val recompensas = ModuloDaRede.recompensas(retrofit)

    @Test
    fun `entrar devolve o refresh no corpo e o perfil da Ana`() = runTest {
        val tokens = identidade.entrarComSenha(PedidoDeLogin("ana@demo.piggu.app", "qualquer", lembrar = true))
        assertEquals("refresh-da-demonstracao", tokens.refreshToken)
        assertTrue(tokens.usuario.ehTitular && tokens.usuario.ehPremium)
        assertEquals(null, tokens.usuario.pendencia)
    }

    @Test
    fun `toda leitura da demo vira modelo`() = runTest {
        val mes = "2026-10"
        val leituras: List<Pair<String, suspend () -> Any?>> = listOf(
            "auth/me" to { identidade.meuPerfil() },
            "family" to { identidade.verFamilia() },
            "family/invites" to { identidade.convitesParaMim() },
            "billing/plan" to { identidade.plano() },
            "expenses" to { financas.listarGastos(mes) },
            "summary" to { financas.resumoDoMes(mes) },
            "categories" to { financas.listarCategorias() },
            "category-rules" to { financas.regrasDeCategoria() },
            "accounts" to { financas.contas() },
            "bills" to { financas.contasFixas(mes) },
            "incomes" to { financas.listarReceitas(mes) },
            "income-categories" to { financas.categoriasDeReceita() },
            "receivables" to { financas.aReceber() },
            "budgets" to { financas.orcamentos(mes) },
            "report" to { financas.relatorioDoAno(2026) },
            "goals" to { financas.listarMetas() },
            "piggy-bank" to { financas.consultarCofrinho() },
            "objectives" to { financas.listarObjetivos() },
            "notes" to { financas.listarNotas() },
            "products" to { financas.listarProdutos() },
            "split" to { financas.acertoDoMes(mes) },
            "settle" to { financas.quemDeveAQuem(mes) },
            "exchange-rate" to { financas.consultarCotacao("BRL", "USD") },
            "currencies" to { financas.listarMoedas() },
            "assistant" to { financas.situacaoDoAssistente() },
            "banking/status" to { bancos.status() },
            "banking/accounts" to { bancos.listarContas() },
            "places" to { estilo.listarLugares() },
            "movies" to { estilo.listarFilmes() },
            "shopping" to { estilo.listarCompras() },
            "monthly-goals" to { estilo.listarMetasDoMes(mes) },
            "girls-math" to { estilo.listarGirlsMath() },
            "feed" to { midia.listarFeed(mes) },
            "coins" to { recompensas.consultarSaldo() },
            "rewards" to { recompensas.listarPremios() },
            "redemptions" to { recompensas.listarResgates() },
        )
        val falhas = leituras.mapNotNull { (rota, ler) ->
            try {
                ler()
                null
            } catch (erro: ErroDaApi.RespostaInvalida) {
                "$rota: ${erro.message}"
            } catch (erro: ErroDaApi.DoServidor) {
                // A demo não responde a rota: o app mostraria o erro, não quebraria.
                if (erro.status == 404) null else "$rota: HTTP ${erro.status}"
            }
        }
        if (falhas.isNotEmpty()) fail("Respostas da demo fora dos modelos:\n" + falhas.joinToString("\n"))
    }
}
