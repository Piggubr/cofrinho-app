package br.com.piggu.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.com.piggu.app.navegacao.Destino
import br.com.piggu.app.navegacao.lerLink
import br.com.piggu.core.data.RetornoDoBanco
import br.com.piggu.core.ui.formatos.lerValor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Locale
import javax.inject.Inject

// FragmentActivity: o BiometricPrompt da trava do app precisa dela.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    /** Os links (piggu://, App Links do piggu.com.br, atalhos) que o app ainda vai abrir. */
    private val links = Channel<Destino>(Channel.BUFFERED)

    @Inject lateinit var retornoDoBanco: RetornoDoBanco

    override fun attachBaseContext(novo: Context) {
        super.attachBaseContext(novo)
        // Os textos do próprio Material (fechar a folha, alça, calendário, busca) seguem o idioma do
        // app, não o do aparelho: com o celular em inglês, o TalkBack lia "Close sheet" (BUG-033).
        applyOverrideConfiguration(Configuration().apply { setLocale(Locale.forLanguageTag("pt-BR")) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Recriada (girou a tela), o link já foi aberto.
        if (savedInstanceState == null) receber(intent)
        setContent { PigguApp(links.receiveAsFlow()) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receber(intent)
    }

    private fun receber(intent: Intent?) {
        val link = intent?.dataString ?: return
        // A volta de conectar banco fica na tela de Cartões e bancos, que estava aberta.
        if (retornoDoBanco.receber(link)) return
        lerLink(link, ::lerValor)?.let { links.trySend(it) }
    }
}
