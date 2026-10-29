package br.com.piggu.feature.relatorios

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** O CSV sai pelo ACTION_SEND com o FileProvider deste módulo, que o outro app consegue ler. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class CompartilharTest {
    @Test
    fun `grava o csv no cache e compartilha pelo FileProvider proprio`() = runTest {
        // Da tela, como na aba: o contexto é a Activity.
        val app = Robolectric.buildActivity(Activity::class.java).setup().get()
        compartilhar(app, CsvDoMes("piggu-gastos-2026-10.csv", "data;item\n".toByteArray()))

        val escolha = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, escolha.action)
        @Suppress("DEPRECATION")
        val envio = escolha.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, envio.action)
        assertEquals("text/csv", envio.type)
        @Suppress("DEPRECATION")
        val endereco = envio.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("${app.packageName}.relatorios.arquivos", endereco.authority)
        assertEquals("/relatorios/piggu-gastos-2026-10.csv", endereco.path)
        assertEquals("data;item\n", File(app.cacheDir, "relatorios/piggu-gastos-2026-10.csv").readText())
    }
}
