package br.com.piggu.feature.perfil

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.Base64

/** prepararFotoDePerfil do site: um quadrado do centro, no máximo 320 px, em JPEG. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FotoDePerfilTest {
    private fun png(largura: Int, altura: Int): ByteArray {
        val imagem = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { imagem.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun lido(base64: String?) = Base64.getDecoder().decode(base64).let { BitmapFactory.decodeByteArray(it, 0, it.size) }

    @Test
    fun `foto grande vira um quadrado de 320 e a pequena nao cresce`() {
        val grande = lido(prepararFotoDePerfil(png(1600, 900)))
        assertEquals(LADO_DA_FOTO, grande.width)
        assertEquals(LADO_DA_FOTO, grande.height)

        val pequena = lido(prepararFotoDePerfil(png(200, 120)))
        assertEquals(120, pequena.width)
        assertEquals(120, pequena.height)
    }

    @Test
    fun `o que nao e imagem nao vai`() {
        assertNull(prepararFotoDePerfil("não é imagem".toByteArray()))
    }
}
