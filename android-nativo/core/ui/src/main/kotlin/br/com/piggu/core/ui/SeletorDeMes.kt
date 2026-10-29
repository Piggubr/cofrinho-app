package br.com.piggu.core.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.ui.formatos.mesPorExtenso
import java.time.YearMonth

/**
 * Setas e o nome do mês (SeletorDeMes do iOS e o cabeçalho de Gastos do site). No mês atual a
 * seta de avançar some: não há gasto no futuro para ver.
 *
 * @param mes aaaa-mm.
 */
@Composable
fun SeletorDeMes(mes: String, mesAtual: String, aoMudar: (String) -> Unit, modifier: Modifier = Modifier) {
    val atual = YearMonth.parse(mes)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton({ aoMudar(atual.minusMonths(1).toString()) }) {
            Icon(painterResource(Icone.Esquerda.desenho), stringResource(R.string.mes_anterior))
        }
        Text(
            mesPorExtenso(mes),
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.titleMedium,
        )
        if (mes < mesAtual) {
            IconButton({ aoMudar(atual.plusMonths(1).toString()) }) {
                Icon(painterResource(Icone.Direita.desenho), stringResource(R.string.proximo_mes))
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}
