package br.com.piggu.feature.login

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/** O que o "Fazer login com o Google" devolveu. Cancelado não é erro: a tela não diz nada. */
sealed interface ResultadoDoGoogle {
    data class Token(val idToken: String) : ResultadoDoGoogle
    data object Cancelado : ResultadoDoGoogle
    data object Falhou : ResultadoDoGoogle
}

/** O botão do Google só aparece com o ID OAuth do Android criado no Google Cloud (D-104). */
val googleDisponivel: Boolean get() = BuildConfig.GOOGLE_NO_ANDROID

/**
 * Sign in with Google pelo Credential Manager. O token é pedido para o ID do site
 * (`serverClientId`), que vai no `aud` e o backend já aceita (D-084); o ID do Android só
 * autoriza o app a pedir.
 */
suspend fun pedirOGoogle(contexto: Context): ResultadoDoGoogle {
    val pedido = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_ID_DO_SITE).build())
        .build()
    return try {
        val credencial = CredentialManager.create(contexto).getCredential(contexto, pedido).credential
        if (credencial is CustomCredential && credencial.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            ResultadoDoGoogle.Token(GoogleIdTokenCredential.createFrom(credencial.data).idToken)
        } else {
            ResultadoDoGoogle.Falhou
        }
    } catch (_: GetCredentialCancellationException) {
        ResultadoDoGoogle.Cancelado
    } catch (_: GetCredentialException) {
        ResultadoDoGoogle.Falhou
    } catch (_: GoogleIdTokenParsingException) {
        ResultadoDoGoogle.Falhou
    }
}

/** Oferece guardar o e-mail e a senha no gerenciador de senhas do aparelho; recusar não muda nada. */
suspend fun oferecerSalvarASenha(contexto: Context, email: String, senha: String) {
    try {
        CredentialManager.create(contexto).createCredential(contexto, CreatePasswordRequest(email, senha))
    } catch (_: CreateCredentialException) {
        // A pessoa disse não, ou o aparelho não tem onde guardar.
    }
}

/** Ao sair: o próximo "Entrar com o Google" pergunta a conta de novo (esquecerConta do site). */
suspend fun esquecerAContaDoGoogle(contexto: Context) {
    runCatching { CredentialManager.create(contexto).clearCredentialState(ClearCredentialStateRequest()) }
}
