package br.com.piggu.app

import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.datastore.PreferenciasDoAparelho
import br.com.piggu.core.ui.HumorDoPorquinho
import br.com.piggu.core.ui.Porquinho
import br.com.piggu.feature.perfil.autenticar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fora do app por menos que isto, a volta não pede a prova. O iOS trava a cada ida ao fundo; no
 * Android o site (Custom Tab), a câmera, a galeria e o próprio PIN tiram o app da frente.
 */
const val TEMPO_FORA_SEM_TRAVAR = 60_000L

/**
 * [travado] nulo enquanto a preferência não chegou: a raiz mostra o "abrindo", sem os valores.
 * [esconderNosRecentes] liga o FLAG_SECURE da janela.
 */
data class EstadoDaTrava(val travado: Boolean? = null, val esconderNosRecentes: Boolean = false)

/**
 * A trava do app (TravaDoApp do iOS), ligada no Perfil: pede a biometria ou o bloqueio de tela ao
 * abrir e ao voltar depois de [TEMPO_FORA_SEM_TRAVAR]. Fica com a atividade: girar a tela não trava,
 * e abrir o app de novo, sim.
 */
@HiltViewModel
class TravaViewModel internal constructor(
    private val preferencias: PreferenciasDoAparelho,
    private val relogio: () -> Long,
) : ViewModel() {
    @Inject constructor(preferencias: PreferenciasDoAparelho) : this(preferencias, SystemClock::elapsedRealtime)

    /** Pedir a prova agora; nulo até ler a preferência. Ligar no Perfil não trava (a prova acabou de passar). */
    private val pedir = MutableStateFlow<Boolean?>(null)
    private var saiuEm: Long? = null

    val estado: StateFlow<EstadoDaTrava> = combine(preferencias.travar, preferencias.esconderNosRecentes, pedir) { ligada, esconder, pedir ->
        EstadoDaTrava(travado = pedir?.let { ligada && it }, esconderNosRecentes = esconder)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EstadoDaTrava())

    init {
        viewModelScope.launch { pedir.value = preferencias.travar.first() }
    }

    fun foiParaOFundo() {
        saiuEm = relogio()
    }

    fun voltou() {
        val saiu = saiuEm ?: return
        saiuEm = null
        if (relogio() - saiu < TEMPO_FORA_SEM_TRAVAR) return
        viewModelScope.launch { if (preferencias.travar.first()) pedir.value = true }
    }

    fun desbloqueou() {
        pedir.value = false
    }
}

/** O FLAG_SECURE na janela da atividade; diálogos, folhas e balões do Compose herdam (SecureFlagPolicy.Inherit). */
@Composable
fun EsconderNosRecentes(esconder: Boolean) {
    val janela = LocalActivity.current?.window ?: return
    LaunchedEffect(esconder) {
        if (esconder) janela.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else janela.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/**
 * O app travado: o porquinho e o "Desbloquear" por cima de tudo. Numa janela própria, como a do
 * iOS, para cobrir também as folhas abertas. Já abre pedindo a prova; o voltar sai do app.
 */
@Composable
fun TelaDeTrava(desbloqueou: () -> Unit) {
    val atividade = LocalActivity.current as? FragmentActivity
    val motivo = stringResource(R.string.trava_motivo)
    val pedir = { atividade?.let { autenticar(it, motivo, desbloqueou) } }
    LaunchedEffect(Unit) { pedir() }
    Dialog(
        onDismissRequest = { atividade?.moveTaskToBack(true) },
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Porquinho(HumorDoPorquinho.Feliz, largura = 160.dp)
                Text(
                    stringResource(R.string.trava_titulo),
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Button({ pedir() }) { Text(stringResource(R.string.trava_desbloquear)) }
            }
        }
    }
}
