package br.com.piggu.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreferenciasDoAparelhoTest {
    @get:Rule val pasta = TemporaryFolder()

    @Test
    fun `trava e esconder nos recentes comecam desligados e guardam a escolha`() = runTest {
        val preferencias = PreferenciasDoAparelho(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { pasta.root.resolve("a.preferences_pb") }))
        assertFalse(preferencias.travar.first())
        assertFalse(preferencias.esconderNosRecentes.first())
        preferencias.mudarTravar(true)
        preferencias.mudarEsconderNosRecentes(true)
        assertTrue(preferencias.travar.first())
        assertTrue(preferencias.esconderNosRecentes.first())
    }
}
