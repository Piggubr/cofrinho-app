package br.com.piggu.app.moldura

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.piggu.app.R
import br.com.piggu.core.designsystem.Icone

/** Os passos do tour do site (core/ui/tutorial.ts), com o mesmo ícone, título e texto. */
private val PASSOS = listOf(
    Triple(Icone.Cofrinho, R.string.tour_inicio_titulo, R.string.tour_inicio_texto),
    Triple(Icone.Mais, R.string.tour_lancar_titulo, R.string.tour_lancar_texto),
    Triple(Icone.Casa, R.string.tour_painel_titulo, R.string.tour_painel_texto),
    Triple(Icone.Alvo, R.string.tour_planejar_titulo, R.string.tour_planejar_texto),
    Triple(Icone.Grafico, R.string.tour_relatorios_titulo, R.string.tour_relatorios_texto),
    Triple(Icone.Pessoas, R.string.tour_familia_titulo, R.string.tour_familia_texto),
    Triple(Icone.Busca, R.string.tour_busca_titulo, R.string.tour_busca_texto),
    Triple(Icone.Brilho, R.string.tour_assistente_titulo, R.string.tour_assistente_texto),
)

/**
 * O tour do Piggu, aberto pelo Perfil: os passos do site em diálogos, sem o balão apontando
 * para o botão (D-118). Pular fecha em qualquer passo.
 */
@Composable
fun TourDoPiggu(fechar: () -> Unit) {
    var passo by rememberSaveable { mutableIntStateOf(0) }
    val (icone, titulo, texto) = PASSOS[passo]
    val ultimo = passo == PASSOS.lastIndex
    AlertDialog(
        onDismissRequest = fechar,
        icon = { Icon(painterResource(icone.desenho), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(titulo)) },
        text = { Text(stringResource(texto)) },
        confirmButton = {
            TextButton({ if (ultimo) fechar() else passo++ }) {
                Text(stringResource(if (ultimo) R.string.tour_concluir else R.string.tour_proximo))
            }
        },
        dismissButton = { if (!ultimo) TextButton(fechar) { Text(stringResource(R.string.tour_pular)) } },
    )
}
