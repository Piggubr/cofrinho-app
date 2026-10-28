package br.com.piggu.feature.planejar

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.ui.R as Comum

/** Um atalho do Planejar: o caminho do site, que o `app` liga à tela. */
data class AtalhoDoPlanejar(val caminho: String, @param:StringRes val titulo: Int, @param:StringRes val descricao: Int, val icone: Icone)

/** Os atalhos de plan-hub.ts, na mesma ordem. */
val ATALHOS_DO_PLANEJAR = listOf(
    AtalhoDoPlanejar("/orcamentos", R.string.planejar_orcamento, R.string.planejar_orcamento_descricao, Icone.Pizza),
    AtalhoDoPlanejar("/contas-fixas", R.string.planejar_contas_do_mes, R.string.planejar_contas_do_mes_descricao, Icone.Recibo),
)

/**
 * Planejar (features/plan-hub e PlanejarView do iOS): o que olha para a frente (quanto gastar e o
 * que vence) num lugar só. Como no site, são só atalhos: nada a carregar, então não há estados de
 * carga, erro ou sem internet, e serve igual às capturas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDoPlanejar(voltar: () -> Unit, abrirTela: (caminho: String) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.planejar_titulo)) },
                navigationIcon = {
                    IconButton(voltar) { Icon(painterResource(Icone.Esquerda.desenho), stringResource(Comum.string.voltar)) }
                },
            )
        },
    ) { margens ->
        Column(
            Modifier.padding(margens).fillMaxSize().verticalScroll(rememberScrollState()).wrapContentWidth().widthIn(max = 720.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ATALHOS_DO_PLANEJAR.forEach { Atalho(it) { abrirTela(it.caminho) } }
        }
    }
}

@Composable
private fun Atalho(atalho: AtalhoDoPlanejar, abrir: () -> Unit) {
    Card(abrir, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(atalho.icone.desenho), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(atalho.titulo), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(atalho.descricao), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(Icone.Direita.desenho), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
