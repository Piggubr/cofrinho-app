package br.com.piggu.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConversaGuardadaTest {
    @get:Rule val pasta = TemporaryFolder()

    @Test
    fun `guarda, le e esquece a conversa`() = runTest {
        val conversa = ConversaGuardada(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { pasta.root.resolve("a.preferences_pb") }))
        assertNull(conversa.ler())
        conversa.guardar("""{"usuario":"u-ana","falas":[]}""")
        assertEquals("""{"usuario":"u-ana","falas":[]}""", conversa.ler())
        conversa.esquecerConversa()
        assertNull(conversa.ler())
    }
}
