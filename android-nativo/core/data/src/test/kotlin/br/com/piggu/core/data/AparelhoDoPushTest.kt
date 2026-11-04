package br.com.piggu.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.piggu.core.testing.RespostaFixa
import br.com.piggu.core.testing.RespostasFixas
import br.com.piggu.core.testing.redeDeTeste
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Push no Android (D-166): o aparelho entra no servidor com o token do FCM e sai junto com a pessoa. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AparelhoDoPushTest {
    @get:Rule val pasta = TemporaryFolder()

    private fun fixas() = RespostasFixas(
        "POST /api/auth/me/devices" to RespostaFixa(204),
        "DELETE /api/auth/me/devices/tok-1" to RespostaFixa(204),
    )

    @Test
    fun `registra o token e, ao sair, tira o aparelho antes de encerrar`() = runTest {
        val fixas = fixas()
        val rede = redeDeTeste(pasta.root, fixas)
        withContext(Dispatchers.IO) { rede.entrar() }
        val aparelho = AparelhoDoPush(rede.sessao, { rede.identidade }, rede.cofre, { "tok-1" }, backgroundScope)

        withContext(Dispatchers.IO) { aparelho.registrar() }
        assertTrue(fixas.pedidos.contains("""POST /api/auth/me/devices {"token":"tok-1","plataforma":"ANDROID"}"""))
        assertEquals("tok-1", rede.cofre.tokenDoPush())

        withContext(Dispatchers.IO) { rede.sessao.sair() }
        val saida = fixas.pedidos.indexOfFirst { it.startsWith("POST /api/auth/sair") || it.contains("/auth/logout") }
        val remocao = fixas.pedidos.indexOf("DELETE /api/auth/me/devices/tok-1")
        assertTrue(remocao >= 0 && (saida < 0 || remocao < saida))
        assertNull(rede.cofre.tokenDoPush())
    }

    @Test
    fun `sem token, como na demo ou com as notificacoes desligadas, nao chama o servidor`() = runTest {
        val fixas = fixas()
        val rede = redeDeTeste(pasta.root, fixas)
        withContext(Dispatchers.IO) { rede.entrar() }
        val aparelho = AparelhoDoPush(rede.sessao, { rede.identidade }, rede.cofre, { null }, backgroundScope)

        withContext(Dispatchers.IO) { aparelho.registrar() }
        assertTrue(fixas.pedidos.none { it.contains("/auth/me/devices") })
        assertNull(rede.cofre.tokenDoPush())
    }
}
