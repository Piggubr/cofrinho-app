package br.com.piggu.app

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

/** O token da sessão nunca vai para um servidor de imagens de fora (catálogo, pôsteres). */
class ImagensSemTokenTest {
    private val usados = mutableListOf<String>()
    private fun cliente(nome: String) = Call.Factory { pedido -> usados += nome; OkHttpClient().newCall(pedido) }

    @Test
    fun `so a API do Piggu vai com a sessao`() {
        val chamadas = chamadasDeImagem("https://piggu.com.br/api/", cliente("sessao"), cliente("fora"))
        chamadas.newCall(Request.Builder().url("https://piggu.com.br/api/assets/a1/content").build())
        chamadas.newCall(Request.Builder().url("https://images.openfoodfacts.org/x.jpg").build())
        chamadas.newCall(Request.Builder().url("https://piggu.com.br.golpe.com/api/x.jpg").build())
        assertEquals(listOf("sessao", "fora", "fora"), usados)
    }
}
