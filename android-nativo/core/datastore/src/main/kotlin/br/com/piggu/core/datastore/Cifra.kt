package br.com.piggu.core.datastore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Cifra e decifra o que fica no disco; o vetor inicial vai junto, na frente. */
interface Cifra {
    fun cifrar(claro: ByteArray): ByteArray
    fun decifrar(cifrado: ByteArray): ByteArray
}

/** AES-GCM com uma chave que nunca sai do Android Keystore (nem para o backup). */
class CifraDoKeystore(private val apelido: String = "piggu_sessao") : Cifra {

    private fun chave(): SecretKey {
        val cofre = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (cofre.getEntry(apelido, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(apelido, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    override fun cifrar(claro: ByteArray): ByteArray {
        val cifra = Cipher.getInstance(TRANSFORMACAO).apply { init(Cipher.ENCRYPT_MODE, chave()) }
        return cifra.iv + cifra.doFinal(claro)
    }

    override fun decifrar(cifrado: ByteArray): ByteArray {
        val cifra = Cipher.getInstance(TRANSFORMACAO)
        cifra.init(Cipher.DECRYPT_MODE, chave(), GCMParameterSpec(128, cifrado, 0, TAMANHO_DO_IV))
        return cifra.doFinal(cifrado, TAMANHO_DO_IV, cifrado.size - TAMANHO_DO_IV)
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMACAO = "AES/GCM/NoPadding"
        const val TAMANHO_DO_IV = 12
    }
}
