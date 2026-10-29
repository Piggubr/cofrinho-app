package br.com.piggu.feature.perfil

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * A biometria forte ou o bloqueio de tela (PIN, padrão, senha), como o .deviceOwnerAuthentication
 * do iOS. Antes do Android 11 a biometria forte com o bloqueio de tela não existe junta: vale a fraca.
 */
private val AUTENTICADORES =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/** Sem biometria nem bloqueio de tela, não há com o que travar o app. */
fun podeTravar(contexto: Context): Boolean =
    BiometricManager.from(contexto).canAuthenticate(AUTENTICADORES) == BiometricManager.BIOMETRIC_SUCCESS

/** Pede a prova de que é a dona do aparelho; [aoPassar] só roda se ela passar. Cancelar não faz nada. */
fun autenticar(atividade: FragmentActivity, titulo: String, aoPassar: () -> Unit) {
    val retorno = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(resultado: BiometricPrompt.AuthenticationResult) = aoPassar()
    }
    val pedido = BiometricPrompt.PromptInfo.Builder().setTitle(titulo).setAllowedAuthenticators(AUTENTICADORES).build()
    BiometricPrompt(atividade, ContextCompat.getMainExecutor(atividade), retorno).authenticate(pedido)
}
