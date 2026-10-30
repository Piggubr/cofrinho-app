package br.com.piggu.feature.perfil

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.Base64

/** LADO_DA_FOTO_DE_PERFIL do site (arquivo.ts). */
internal const val LADO_DA_FOTO = 320

/**
 * prepararFotoDePerfil do site: recorta um quadrado no centro, reduz para no máximo 320 px e
 * regrava em JPEG 85 com fundo branco. Regravar descarta o EXIF (onde a câmera guarda a
 * localização) já no aparelho; a orientação do EXIF é aplicada antes, como o navegador faz.
 *
 * @return o JPEG em base64, ou nulo quando não é uma imagem que dá para ler
 */
internal fun prepararFotoDePerfil(bytes: ByteArray): String? {
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, limites)
    val menor = minOf(limites.outWidth, limites.outHeight)
    if (menor <= 0) return null
    // Uma foto de 12 MP inteira na memória não cabe em todo celular: lê já reduzida, sem passar de 320.
    var amostra = 1
    while (menor / (amostra * 2) >= LADO_DA_FOTO) amostra *= 2
    val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = amostra }) ?: return null
    val lado = minOf(original.width, original.height)
    val destino = minOf(LADO_DA_FOTO, lado)
    val escala = destino.toFloat() / lado
    val matriz = Matrix().apply {
        postScale(escala, escala)
        postRotate(giroDoExif(bytes))
    }
    val quadrado = Bitmap.createBitmap(original, (original.width - lado) / 2, (original.height - lado) / 2, lado, lado, matriz, true)
    // PNG com transparência fica com fundo branco, como o canvas do site.
    val final = createBitmap(quadrado.width, quadrado.height)
    Canvas(final).apply {
        drawColor(Color.WHITE)
        drawBitmap(quadrado, 0f, 0f, null)
    }
    val jpeg = ByteArrayOutputStream().also { final.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    return Base64.getEncoder().encodeToString(jpeg)
}

// O do Android lê de um InputStream desde a API 24 (minSdk 26): o androidx.exifinterface seria uma dependência a mais.
@SuppressLint("ExifInterface")
private fun giroDoExif(bytes: ByteArray): Float {
    val orientacao = runCatching {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    return when (orientacao) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
}

/**
 * A foto do perfil para mostrar: a própria vem como `data:image/jpeg;base64,...` (FotoDePerfil.java)
 * e a do Google como endereço https. Nula quando não carrega: a tela mostra a inicial. Bloqueia;
 * chame fora do Main.
 */
internal fun lerFotoDoPerfil(foto: String): Bitmap? = runCatching {
    when {
        foto.startsWith("data:") -> Base64.getDecoder().decode(foto.substringAfter(',')).let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        foto.startsWith("https://") -> URL(foto).openStream().use(BitmapFactory::decodeStream)
        else -> null
    }
}.getOrNull()

/** O FileProvider da foto tirada pela câmera (AndroidManifest.xml deste módulo). */
class ArquivosDoPerfil : FileProvider()
