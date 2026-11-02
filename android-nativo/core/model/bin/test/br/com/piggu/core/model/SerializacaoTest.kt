package br.com.piggu.core.model

import java.math.BigDecimal
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JSON no formato que o backend devolve (records do Spring serializados pelo Jackson). */
class SerializacaoTest {

    private fun gasto(valor: String) = """
        {"id":"g1","data":"2026-10-05","item":"Pão","categoria":"Mercado","valor":$valor}
    """

    @Test
    fun `dinheiro chega sem perder precisao, em numero ou em texto`() {
        for (valor in listOf("0.1", "12.50", "1234567.89", "-850.10", "99999999999999.99")) {
            assertEquals(BigDecimal(valor), JsonDoPiggu.decodeFromString<Gasto>(gasto(valor)).valor)
            assertEquals(BigDecimal(valor), JsonDoPiggu.decodeFromString<Gasto>(gasto("\"$valor\"")).valor)
        }
    }

    @Test
    fun `dinheiro vai como numero, com as casas que tem`() {
        val json = JsonDoPiggu.encodeToString(PedidoDeOrcamento("Mercado", BigDecimal("12.50")))

        assertEquals("""{"categoria":"Mercado","limite":12.50}""", json)
    }

    @Test
    fun `mapa de metas guarda o decimal`() {
        val metas = JsonDoPiggu.decodeFromString(
            MapSerializer(String.serializer(), SerializadorDeDecimal),
            """{"2026-10":2500.5}""",
        )

        assertEquals(BigDecimal("2500.5"), metas["2026-10"])
    }

    @Test
    fun `enum desconhecido nao quebra, solto ou em lista`() {
        val membro = JsonDoPiggu.decodeFromString<MembroDaFamilia>(
            """{"id":"m1","nome":"Ana","papel":"AVO","premium":false}""",
        )
        val preferencias = JsonDoPiggu.decodeFromString<Preferencias>(
            """{"modulos":["filmes","novidade","girls-math"]}""",
        )
        val acoes = JsonDoPiggu.decodeFromString(
            ListSerializer(AcaoDeAuditoria.serializer()),
            """["CRIOU","ARQUIVOU"]""",
        )

        assertEquals(PigguRole.DESCONHECIDO, membro.papel)
        assertEquals(
            listOf(ModuloDeEstiloDeVida.FILMES, ModuloDeEstiloDeVida.DESCONHECIDO, ModuloDeEstiloDeVida.GIRLS_MATH),
            preferencias.modulos,
        )
        assertEquals(listOf(AcaoDeAuditoria.CRIOU, AcaoDeAuditoria.DESCONHECIDO), acoes)
    }

    @Test
    fun `enum vai com o nome do JSON`() {
        assertEquals(
            """{"modulos":["girls-math","compras"]}""",
            JsonDoPiggu.encodeToString(PedidoDeModulos(listOf(ModuloDeEstiloDeVida.GIRLS_MATH, ModuloDeEstiloDeVida.COMPRAS))),
        )
        assertEquals(
            """{"item":"Pão","lista":"Desejos"}""",
            JsonDoPiggu.encodeToString(NovoItemDeCompra("Pão", lista = ListaDeCompras.DESEJOS)),
        )
    }

    @Test
    fun `campo desconhecido e ignorado e ausente vira nulo ou lista vazia`() {
        val resumo = JsonDoPiggu.decodeFromString<ResumoDoMes>(
            """{"mes":"2026-10","receitas":0,"gastos":850.10,"sobra":-850.10,"taxaDePoupanca":null,
                "gastosMesAnterior":0,"campoDoFuturo":{"x":1}}""",
        )

        assertNull(resumo.taxaDePoupanca)
        assertNull(resumo.variacao)
        assertTrue(resumo.porCategoria.isEmpty())
        assertEquals(BigDecimal("-850.10"), resumo.sobra)
    }

    @Test
    fun `par de tokens com o perfil completo`() {
        val tokens = JsonDoPiggu.decodeFromString<ParDeTokens>(
            """
            {"accessToken":"eyJ.abc.def","expiresIn":300,"refreshToken":"r1",
             "usuario":{"id":"u1","email":"ana@example.com","nome":"Ana Souza","primeiroNome":"Ana",
              "apelido":null,"foto":null,"role":"TITULAR","ativo":true,"permissoes":{"assistente":true},
              "preferencias":{"moeda":"BRL"},"plano":"PREMIUM","premiumAte":"2027-01-31T23:59:59Z",
              "familia":"f1","avisos":{"email":true,"push":false},"termosAceitos":"2026-05",
              "nascimento":"1992-03-14",
              "compartilhamento":{"mostraTudoAoParceiro":false,"compartilharPorPadrao":true}}}
            """,
        )

        assertEquals("r1", tokens.refreshToken)
        assertEquals(PigguRole.TITULAR, tokens.usuario.role)
        assertEquals(Plano.PREMIUM, tokens.usuario.plano)
        assertEquals("BRL", tokens.usuario.preferencias?.moeda)
        assertNull(tokens.usuario.preferencias?.modulos)
        assertNull(tokens.usuario.cadastroCompleto)
    }

    @Test
    fun `pedido leva o padrao e deixa o nulo de fora`() {
        assertEquals(
            """{"email":"ana@x.com","senha":"s","lembrar":true,"refreshNoCorpo":true}""",
            JsonDoPiggu.encodeToString(PedidoDeLogin("ana@x.com", "s", lembrar = true)),
        )
        val lancamento = NovoLancamento(
            data = "2026-10-27",
            itens = listOf(ItemDeGasto("Fone", valor = BigDecimal("299.9"), tipo = "Variavel")),
            parcelas = 3,
        )
        assertEquals(
            """{"data":"2026-10-27","itens":[{"item":"Fone","valor":299.9,"tipo":"Variavel"}],"parcelas":3}""",
            JsonDoPiggu.encodeToString(lancamento),
        )
    }
}
