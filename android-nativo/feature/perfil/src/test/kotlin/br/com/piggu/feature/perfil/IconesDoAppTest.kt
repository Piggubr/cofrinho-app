package br.com.piggu.feature.perfil

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.piggu.core.datastore.PreferenciasDoAparelho
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** O ícone do app (IconeDoAppPlugin.java e icone-do-app.ts): um activity-alias ligado por vez. */
@RunWith(AndroidJUnit4::class)
class IconesDoAppTest {
    @get:Rule val pasta = TemporaryFolder()
    private val contexto: Context = ApplicationProvider.getApplicationContext()

    private fun ligados() = IconeDoApp.entries.filter {
        contexto.packageManager.getComponentEnabledSetting(ComponentName(contexto, it.classe)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    @Test
    fun `escolher liga so o alias do icone e o Premium que acabou volta ao padrao`() = runTest {
        val preferencias = PreferenciasDoAparelho(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { pasta.newFile("a.preferences_pb") }))
        val icones = IconesDoApp(contexto, preferencias)
        assertEquals(IconeDoApp.Padrao, icones.atual())
        assertEquals("br.com.piggu.app.IconeNamorados", IconeDoApp.Namorados.classe)

        icones.escolher(IconeDoApp.Azul)
        assertEquals(listOf(IconeDoApp.Azul), ligados())
        assertEquals(IconeDoApp.Azul, icones.atual())

        // Com Premium nada muda; sem, o Azul volta ao Neon e a cor grátis fica.
        preferencias.mudarCor("menta")
        assertFalse(icones.voltarAoPadraoSemPremium(premium = true))
        assertEquals(IconeDoApp.Azul, icones.atual())
        assertTrue(icones.voltarAoPadraoSemPremium(premium = false))
        assertEquals(listOf(IconeDoApp.Padrao), ligados())
        assertEquals(IconeDoApp.Padrao, icones.atual())
        assertEquals("menta", preferencias.cor.first())
        assertTrue(preferencias.voltouAoPadrao.first())

        // O Clássico é grátis: fica.
        preferencias.esquecerQueVoltouAoPadrao()
        icones.escolher(IconeDoApp.Classico)
        assertFalse(icones.voltarAoPadraoSemPremium(premium = false))
        assertEquals(listOf(IconeDoApp.Classico), ligados())
        assertFalse(preferencias.voltouAoPadrao.first())
    }
}
