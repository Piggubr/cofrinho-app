package br.com.piggu.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/** O Keystore do Android não existe na JVM: a mesma cifra, com uma chave na memória. */
private class CifraDeTeste : Cifra {
    private val chave = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    override fun cifrar(claro: ByteArray): ByteArray {
        val cifra = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, chave) }
        return cifra.iv + cifra.doFinal(claro)
    }
    override fun decifrar(cifrado: ByteArray): ByteArray {
        val cifra = Cipher.getInstance("AES/GCM/NoPadding")
        cifra.init(Cipher.DECRYPT_MODE, chave, GCMParameterSpec(128, cifrado, 0, 12))
        return cifra.doFinal(cifrado, 12, cifrado.size - 12)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CofreDaSessaoTest {
    @get:Rule val pasta = TemporaryFolder()

    private fun TestScope.cofre(arquivo: File, cifra: Cifra = CifraDeTeste()) = CofreDaSessao(
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { arquivo }),
        cifra,
    )

    @Test
    fun `guarda o refresh cifrado e le de volta`() = runTest(UnconfinedTestDispatcher()) {
        val arquivo = pasta.root.resolve("sessao.preferences_pb")
        val cofre = cofre(arquivo)
        cofre.guardarRefresh("refresh-secreto")
        assertEquals("refresh-secreto", cofre.refresh())
        // No disco, nunca o texto puro.
        assertEquals(false, String(arquivo.readBytes(), Charsets.ISO_8859_1).contains("refresh-secreto"))
    }

    @Test
    fun `refresh que nao decifra vale como sessao nenhuma`() = runTest(UnconfinedTestDispatcher()) {
        // Como a chave do Keystore depois de restaurar o aparelho: o que está no disco não abre mais.
        val semAChave = object : Cifra by CifraDeTeste() {
            override fun decifrar(cifrado: ByteArray): ByteArray = throw javax.crypto.AEADBadTagException()
        }
        val cofre = cofre(pasta.root.resolve("sessao.preferences_pb"), semAChave)
        cofre.guardarRefresh("r1")
        assertNull(cofre.refresh())
    }

    @Test
    fun `apagar leva o refresh e o perfil`() = runTest(UnconfinedTestDispatcher()) {
        val cofre = cofre(pasta.root.resolve("sessao.preferences_pb"))
        cofre.guardarRefresh("r1")
        cofre.guardarPerfil("{\"id\":\"u1\"}")
        cofre.apagar()
        assertNull(cofre.refresh())
        assertNull(cofre.perfil())
    }
}
