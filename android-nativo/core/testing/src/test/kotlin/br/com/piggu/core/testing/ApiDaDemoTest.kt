package br.com.piggu.core.testing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime

/** A API falsa da demonstração responde como o backend e guarda as escritas em memória (porte de api-da-demo.spec.ts). */
class ApiDaDemoTest {
    private val fuso = ZoneId.of("America/Sao_Paulo")
    private val relogio = Clock.fixed(ZonedDateTime.of(2026, 10, 28, 10, 0, 0, 0, fuso).toInstant(), fuso)
    private val mes = "2026-10"
    private var cliente = clienteCom(ApiDaDemo(relogio))

    private fun clienteCom(api: ApiDaDemo) = OkHttpClient.Builder().addInterceptor(api).build()

    private class Resposta(val status: Int, val corpo: JsonElement)

    private fun chamar(metodo: String, caminho: String, corpo: String? = null, base: String = "https://demo.piggu.invalid"): Resposta {
        val pedido = Request.Builder().url(base + caminho)
            .method(metodo, corpo?.toRequestBody("application/json".toMediaType()) ?: if (metodo == "GET" || metodo == "DELETE") null else "".toRequestBody())
            .build()
        cliente.newCall(pedido).execute().use { resposta ->
            val texto = resposta.body.string()
            return Resposta(resposta.code, if (texto.isEmpty()) JsonNull else Json.parseToJsonElement(texto))
        }
    }

    private fun get(caminho: String) = chamar("GET", caminho).also { assertEquals(caminho, 200, it.status) }.corpo
    private fun post(caminho: String, corpo: String = "{}") = chamar("POST", caminho, corpo)

    private val JsonElement.obj get() = jsonObject
    private operator fun JsonElement.get(chave: String) = jsonObject.getValue(chave)
    private val JsonElement.texto get() = jsonPrimitive.content
    private val JsonElement.decimal get() = BigDecimal(jsonPrimitive.content)

    @Test
    fun `entra sem login como a titular Premium da Familia Demo`() {
        val tokens = post("/api/auth/refresh").corpo
        assertEquals("PREMIUM", tokens["usuario"]["plano"].texto)
        assertEquals("TITULAR", tokens["usuario"]["role"].texto)
        assertEquals("Família Demo", get("/api/family")["nome"].texto)
    }

    @Test
    fun `entrar com a Apple tambem entra como a titular`() {
        val tokens = post("/api/auth/apple", """{"idToken":"x","nonce":"y"}""").corpo
        assertEquals("TITULAR", tokens["usuario"]["role"].texto)
    }

    @Test
    fun `login com refreshNoCorpo devolve o refresh e o me traz a conta`() {
        val tokens = post("/api/auth/entrar", """{"email":"qualquer@x.com","senha":"qualquer","refreshNoCorpo":true}""")
        assertEquals(201, tokens.status)
        assertNotNull(tokens.corpo.obj["refreshToken"])
        assertEquals(1800, tokens.corpo["expiresIn"].texto.toInt())
        // Renovar pelo corpo devolve o novo no corpo; o site (cookie) não vê o refresh.
        assertNotNull(post("/api/auth/refresh", """{"refreshToken":"refresh-da-demonstracao"}""").corpo.obj["refreshToken"])
        assertEquals(null, post("/api/auth/refresh").corpo.obj["refreshToken"])

        val eu = get("/api/auth/me")
        assertEquals("ana@demo.piggu.app", eu["email"].texto)
        assertEquals("true", eu["cadastroCompleto"].texto)
        assertEquals("2027-10-28T03:00:00Z", eu["premiumAte"].texto)
    }

    @Test
    fun `lista os gastos do mes do mais novo para o mais velho`() {
        val gastos = get("/api/expenses?mes=$mes").jsonArray
        assertTrue(gastos.size >= 10)
        assertTrue(gastos.all { it["data"].texto.startsWith(mes) })
        assertEquals(gastos.map { it["data"].texto }.sortedDescending(), gastos.map { it["data"].texto })
        // Contas fixas que venceram até hoje (dia 28) estão lançadas; a academia (dia 25) ficou vencida.
        assertTrue(gastos.any { it["item"].texto == "Aluguel" && it["valor"].texto == "1800" })
        val academia = get("/api/bills?mes=$mes").jsonArray.single { it["id"].texto == "fixa-academia" }
        assertEquals("VENCIDA", academia["situacao"].texto)
    }

    @Test
    fun `o resumo do mes fecha a conta e lancar um gasto muda o total`() {
        val antes = get("/api/reports/month?mes=$mes")
        assertEquals(antes["receitas"].decimal - antes["gastos"].decimal, antes["sobra"].decimal)

        val lancados = post("/api/expenses", """{"data":"$mes-01","itens":[{"item":"Teste","valor":100}]}""")
        assertEquals(201, lancados.status)
        val depois = get("/api/reports/month?mes=$mes")
        assertEquals(antes["gastos"].decimal + BigDecimal(100), depois["gastos"].decimal)
    }

    @Test
    fun `lancar um gasto aparece na lista e no resumo`() {
        val novo = post("/api/expenses", """{"data":"$mes-27","itens":[{"item":"Pizza","valor":"45.5","categoria":"Lazer"}]}""")
            .corpo.jsonArray.single()
        assertEquals("Lazer", novo["categoria"].texto)
        assertEquals("u-ana", novo["usuario"].texto)

        assertTrue(get("/api/expenses?mes=$mes").jsonArray.any { it["id"] == novo["id"] })
        val lazer = get("/api/reports/month?mes=$mes")["porCategoria"].jsonArray.single { it["categoria"].texto == "Lazer" }
        // Jantar de sexta (156) + Cinema (64) + a pizza.
        assertEquals(BigDecimal("265.5"), lazer["total"].decimal)
    }

    @Test
    fun `parcelado vira uma linha por mes e a regra de categoria vale`() {
        val linhas = post("/api/expenses", """{"data":"$mes-01","parcelas":3,"itens":[{"item":"Uber viagem","valor":100}]}""").corpo.jsonArray
        assertEquals(listOf("33.33", "33.33", "33.34"), linhas.map { it["valor"].texto })
        assertEquals(listOf("2026-10-01", "2026-11-01", "2026-12-01"), linhas.map { it["data"].texto })
        assertEquals("Transporte", linhas[0]["categoria"].texto)
    }

    @Test
    fun `apagar um gasto tira da lista e o segundo apagar responde 404`() {
        val alvo = get("/api/expenses?mes=$mes").jsonArray.first()["id"].texto
        assertEquals(204, chamar("DELETE", "/api/expenses/$alvo").status)
        assertTrue(get("/api/expenses?mes=$mes").jsonArray.none { it["id"].texto == alvo })

        val deNovo = chamar("DELETE", "/api/expenses/$alvo")
        assertEquals(404, deNovo.status)
        assertEquals("NAO_ENCONTRADO", deNovo.corpo["codigo"].texto)
        assertEquals("Gasto não encontrado.", deNovo.corpo["erro"].texto)
    }

    @Test
    fun `o ano tem 12 meses e o cartao tem fatura com o fone parcelado`() {
        assertEquals(12, get("/api/reports/year?ano=2026")["meses"].jsonArray.size)
        val cartao = get("/api/accounts").jsonArray.single { it["tipo"].texto == "CARTAO" }
        assertNotEquals(JsonNull, cartao["faturaAberta"])
        assertEquals(null, cartao.obj["ultimaFaturaPaga"])
    }

    @Test
    fun `pagar conta fixa lanca o gasto, pagamento e Pluggy respondem que sao indisponiveis`() {
        assertEquals(201, post("/api/bills/fixa-academia/pay?mes=$mes").status)
        val academia = get("/api/bills?mes=$mes").jsonArray.single { it["id"].texto == "fixa-academia" }
        assertEquals("PAGA", academia["situacao"].texto)
        assertEquals(409, post("/api/bills/fixa-academia/pay?mes=$mes").status)

        val falha = post("/api/billing/checkout", """{"periodo":"MENSAL"}""")
        assertEquals(422, falha.status)
        assertTrue(falha.corpo["erro"].texto.contains("demonstração"))
        assertEquals(422, post("/api/banking/connect-token").status)
    }

    @Test
    fun `erro vem no formato do backend`() {
        val falha = chamar("DELETE", "/api/me")
        assertEquals(422, falha.status)
        assertEquals(setOf("erro", "codigo", "campos", "momento"), falha.corpo.obj.keys)
        assertEquals("DEMO", falha.corpo["codigo"].texto)
        assertEquals(JsonArray(emptyList()), falha.corpo["campos"])
        assertEquals("2026-10-28T13:00:00Z", falha.corpo["momento"].texto)
    }

    @Test
    fun `rota desconhecida responde 404 sem sair para a rede`() {
        MockWebServer().use { servidor ->
            servidor.start()
            val falha = chamar("GET", "/api/nao-existe", base = servidor.url("/").toString().trimEnd('/'))
            assertEquals(404, falha.status)
            assertEquals("Indisponível na demonstração: aqui os dados são de exemplo.", falha.corpo["erro"].texto)
            assertEquals(0, servidor.requestCount)
        }
    }

    @Test
    fun `resgatar premio desconta as moedas e sem saldo responde SALDO_INSUFICIENTE`() {
        assertEquals("120", get("/api/coins")["saldo"].texto)
        val resgate = post("/api/prizes/premio-cinema/redemptions").corpo
        assertEquals("40", resgate["saldo"].texto)
        val semSaldo = post("/api/prizes/premio-cinema/redemptions")
        assertEquals(422, semSaldo.status)
        assertEquals("SALDO_INSUFICIENTE", semSaldo.corpo["codigo"].texto)
    }

    @Test
    fun `mesmo relogio da os mesmos ids`() {
        val corpo = """{"data":"$mes-02","itens":[{"item":"Pão","valor":7}]}"""
        val primeiro = post("/api/expenses", corpo).corpo
        cliente = clienteCom(ApiDaDemo(relogio))
        assertEquals(primeiro, post("/api/expenses", corpo).corpo)
    }

    @Test
    fun `reenvio com o mesmo idDoEnvio nao lanca de novo`() {
        val corpo = """{"idDoEnvio":"e1","data":"$mes-02","itens":[{"item":"Pão","valor":7}]}"""
        assertEquals(post("/api/expenses", corpo).corpo, post("/api/expenses", corpo).corpo)
        assertEquals(1, get("/api/expenses?mes=$mes").jsonArray.count { it["item"].texto == "Pão" })
    }

    @Test
    fun `importar extrato le o CSV, aplica a regra e a reimportacao pula`() {
        val csv = "data;descricao;valor\\n01/10/2026;Uber Centro;-1.023,50\\n02/10/2026;Salário;6500,00"
        val linhas = post("/api/expenses/import/preview", """{"conteudo":"$csv"}""").corpo.jsonArray
        assertEquals(1, linhas.size)
        assertEquals("2026-10-01", linhas[0]["data"].texto)
        assertEquals("1023.5", linhas[0]["valor"].texto)
        assertEquals("Transporte", linhas[0]["categoria"].texto)

        val importar = """{"linhas":[${linhas[0]}]}"""
        assertEquals("1", post("/api/expenses/import", importar).corpo["importados"].texto)
        assertEquals("1", post("/api/expenses/import", importar).corpo["pulados"].texto)
    }

    @Test
    fun `foto do mural vem da memoria`() {
        val assetId = get("/api/feed").jsonArray.first()["assetId"].texto
        val pedido = Request.Builder().url("https://demo.piggu.invalid/api/assets/$assetId/content").build()
        cliente.newCall(pedido).execute().use {
            assertEquals("image/svg+xml", it.body.contentType().toString())
            assertTrue(it.body.string().startsWith("<svg"))
        }
    }

    @Test
    fun `todo dinheiro sai com no maximo duas casas`() {
        post("/api/expenses", """{"data":"$mes-03","parcelas":7,"dividirCom":["u-ana","u-beto","u-x"],"itens":[{"item":"Sofá","valor":"1000.01"}]}""")
        val rotas = listOf(
            "/api/expenses", "/api/expenses?mes=$mes", "/api/expenses/splits?mes=$mes", "/api/expenses/splits/transfers?mes=$mes",
            "/api/reports/month?mes=$mes", "/api/reports/month?mes=2026-09", "/api/reports/year?ano=2026", "/api/bills?mes=$mes",
            "/api/budgets?mes=$mes", "/api/accounts", "/api/accounts/conta-nubank/statement?mes=$mes", "/api/incomes?mes=$mes",
            "/api/monthly-goals", "/api/piggy-bank", "/api/piggy-bank/goals", "/api/products", "/api/notes", "/api/girls-math",
            "/api/places", "/api/banking/accounts", "/api/banking/transactions", "/api/coins", "/api/prizes", "/api/billing/plan",
            "/api/family", "/api/auth/me",
        )
        for (rota in rotas) {
            numeros(get(rota)).forEach { (chave, valor) ->
                assertTrue("$rota: $chave = $valor", BigDecimal(valor).stripTrailingZeros().scale() <= 2)
            }
        }
    }

    private fun numeros(elemento: JsonElement, chave: String = ""): List<Pair<String, String>> = when (elemento) {
        is JsonObject -> elemento.flatMap { (k, v) -> numeros(v, k) }
        is JsonArray -> elemento.flatMap { numeros(it, chave) }
        is JsonPrimitive -> if (!elemento.isString && elemento != JsonNull && elemento.content.first().let { it.isDigit() || it == '-' }) listOf(chave to elemento.content) else emptyList()
    }
}
