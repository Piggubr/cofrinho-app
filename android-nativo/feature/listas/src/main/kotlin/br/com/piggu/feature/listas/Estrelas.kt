package br.com.piggu.feature.listas

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.piggu.core.designsystem.Icone

/**
 * Cinco estrelas tocáveis, a nota de lugares e filmes (Estrelas do iOS): as até a [nota] ficam
 * coloridas. Cada uma é um botão de 48dp, com a [descricao] dela para o TalkBack.
 */
@Composable
internal fun Estrelas(nota: Int, aoEscolher: (Int) -> Unit, descricao: @Composable (Int) -> String, modifier: Modifier = Modifier) {
    Row(modifier) {
        for (valor in 1..5) {
            IconButton({ aoEscolher(valor) }, Modifier.semantics { selected = nota == valor }) {
                Icon(
                    painterResource(Icone.Estrela.desenho),
                    descricao(valor),
                    Modifier.size(24.dp),
                    tint = if (nota >= valor) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}
