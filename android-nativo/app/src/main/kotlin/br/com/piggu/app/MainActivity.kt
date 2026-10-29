package br.com.piggu.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.com.piggu.app.navegacao.Destino
import br.com.piggu.app.navegacao.lerLink
import br.com.piggu.core.ui.formatos.lerValor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** Os links (piggu://, App Links do piggu.com.br, atalhos) que o app ainda vai abrir. */
    private val links = Channel<Destino>(Channel.BUFFERED)

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
        lerLink(link, ::lerValor)?.let { links.trySend(it) }
    }
}
