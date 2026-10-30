package br.com.piggu.feature.painel

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import br.com.piggu.core.designsystem.Icone

/**
 * Personalizar o Painel (a lista de organizar do Perfil do site): o interruptor mostra ou esconde
 * o cartão, a alça arrasta, e o TalkBack tem "Mover para cima" e "Mover para baixo".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrganizarPainel(blocos: List<BlocoDoPainel>, eventos: EventosDoPainel, fechar: () -> Unit) {
    // Sem o arrastar da folha: o arrastar é o da alça.
    ModalBottomSheet(fechar, sheetGesturesEnabled = false) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.personalizar), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                TextButton(fechar) { Text(stringResource(R.string.organizar_pronto)) }
            }
            blocos.forEachIndexed { indice, bloco ->
                key(bloco.id) { LinhaParaOrganizar(bloco, indice, blocos.size, eventos) }
            }
            Text(
                stringResource(R.string.organizar_rodape),
                Modifier.padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(eventos.restaurarPainel) { Text(stringResource(R.string.organizar_padrao)) }
        }
    }
}

@Composable
private fun LinhaParaOrganizar(bloco: BlocoDoPainel, indice: Int, total: Int, eventos: EventosDoPainel) {
    val id = bloco.bloco
    var deslocamento by remember { mutableFloatStateOf(0f) }
    var altura by remember { mutableIntStateOf(0) }
    // O gesto continua enquanto a linha troca de lugar: lê sempre a posição de agora.
    val agora by rememberUpdatedState(indice)
    val quantos by rememberUpdatedState(total)
    val moverPara by rememberUpdatedState(eventos.moverBlocoPara)
    val subir = stringResource(R.string.organizar_subir)
    val descer = stringResource(R.string.organizar_descer)
    val acoes = buildList {
        if (indice > 0) add(CustomAccessibilityAction(subir) { eventos.moverBloco(id, -1); true })
        if (indice < total - 1) add(CustomAccessibilityAction(descer) { eventos.moverBloco(id, 1); true })
    }
    Surface(
        Modifier
            .zIndex(if (deslocamento != 0f) 1f else 0f)
            .graphicsLayer { translationY = deslocamento }
            .onSizeChanged { altura = it.height },
        tonalElevation = if (deslocamento != 0f) 6.dp else 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(bloco.visivel, role = Role.Switch) { eventos.alternarBloco(id) }
                .semantics { customActions = acoes },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painterResource(Icone.Arrastar.desenho),
                null,
                Modifier
                    .size(48.dp)
                    .pointerInput(id) {
                        detectDragGestures(onDragEnd = { deslocamento = 0f }, onDragCancel = { deslocamento = 0f }) { mudanca, arrasto ->
                            mudanca.consume()
                            deslocamento += arrasto.y
                            // Passou da metade da linha vizinha: troca de lugar e o desenho acompanha.
                            if (altura > 0 && deslocamento > altura / 2f && agora < quantos - 1) {
                                moverPara(id, agora + 1)
                                deslocamento -= altura
                            } else if (altura > 0 && deslocamento < -altura / 2f && agora > 0) {
                                moverPara(id, agora - 1)
                                deslocamento += altura
                            }
                        }
                    }
                    .padding(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(id.nome), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(bloco.visivel, null)
        }
    }
}
