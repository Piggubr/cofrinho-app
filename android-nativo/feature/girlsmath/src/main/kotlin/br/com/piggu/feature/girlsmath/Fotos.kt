package br.com.piggu.feature.girlsmath

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

/** O FileProvider da foto tirada pela câmera (AndroidManifest.xml deste módulo). */
class FotosDoGirlsMath : FileProvider()

/** Lado maior da foto enviada, como o Imagem.fotoDeProduto do iOS: fica bem abaixo dos 5 MB do backend. */
internal const val LADO_DA_FOTO = 1600

/**
 * Lê a foto escolhida ([abrir] dá um fluxo novo a cada chamada), põe em pé pelo EXIF, reduz o lado
 * maior a [lado] e regrava em JPEG. Regravar descarta o EXIF (onde fica a localização). Nulo quando
 * não é imagem que o Android leia.
 */
internal fun reduzirFoto(abrir: () -> InputStream?, lado: Int = LADO_DA_FOTO): ByteArray? {
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    // Só lê o tamanho: o decodeStream devolve nulo aqui mesmo dando certo.
    abrir()?.use { BitmapFactory.decodeStream(it, null, limites) }
    val maior = max(limites.outWidth, limites.outHeight)
    if (maior <= 0) return null
    // Lê já reduzida pela potência de 2 que ainda deixa o lado maior acima do alvo: poupa memória.
    var amostra = 1
    while (maior / (amostra * 2) >= lado) amostra *= 2
    val lida = abrir()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = amostra }) } ?: return null
    val graus = abrir()?.use { ExifInterface(it).rotationDegrees } ?: 0
    val escala = min(1f, lado.toFloat() / max(lida.width, lida.height))
    val matriz = Matrix().apply {
        postScale(escala, escala)
        postRotate(graus.toFloat())
    }
    val final = Bitmap.createBitmap(lida, 0, 0, lida.width, lida.height, matriz, true)
    return ByteArrayOutputStream().also { final.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
}

/**
 * A imagem para a tela, lida já pequena (por volta de [lado] px). Nulo quando o Android não lê:
 * as fotos de exemplo da demonstração são SVG, e aí a tela mostra o marcador.
 */
internal fun lerParaATela(bytes: ByteArray, lado: Int): Bitmap? {
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, limites)
    val maior = max(limites.outWidth, limites.outHeight)
    if (maior <= 0) return null
    var amostra = 1
    while (maior / (amostra * 2) >= lado) amostra *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = amostra })
}
