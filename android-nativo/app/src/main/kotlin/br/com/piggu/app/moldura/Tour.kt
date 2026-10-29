package br.com.piggu.app.moldura

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipScope
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.piggu.app.R
import br.com.piggu.app.navegacao.Tela
import br.com.piggu.core.designsystem.Icone

/** Para onde o balão aponta (os data-tour do site); [tela] liga o alvo à aba ou ao item do menu. */
enum class AlvoDoTour(val tela: Tela? = null) {
    Lancar,
    Busca,
    Assistente,
    Painel(Tela.Painel),
    Planejar(Tela.Planejar),
    Relatorios(Tela.Relatorios),
    Familia(Tela.Familia),
    Mais(Tela.Mais),
    ;

    companion object {
        fun da(tela: Tela): AlvoDoTour? = entries.firstOrNull { it.tela == tela }
    }
}

private class PassoDoTour(val icone: Icone, @StringRes val titulo: Int, @StringRes val texto: Int, val alvos: List<AlvoDoTour> = emptyList())

/** Os passos do site (core/ui/tutorial.ts), com o mesmo ícone, título, texto e alvos, em ordem de preferência. */
private val PASSOS = listOf(
    PassoDoTour(Icone.Cofrinho, R.string.tour_inicio_titulo, R.string.tour_inicio_texto),
    PassoDoTour(Icone.Mais, R.string.tour_lancar_titulo, R.string.tour_lancar_texto, listOf(AlvoDoTour.Lancar)),
    PassoDoTour(Icone.Casa, R.string.tour_painel_titulo, R.string.tour_painel_texto, listOf(AlvoDoTour.Painel)),
    PassoDoTour(Icone.Alvo, R.string.tour_planejar_titulo, R.string.tour_planejar_texto, listOf(AlvoDoTour.Planejar, AlvoDoTour.Mais)),
    PassoDoTour(Icone.Grafico, R.string.tour_relatorios_titulo, R.string.tour_relatorios_texto, listOf(AlvoDoTour.Relatorios, AlvoDoTour.Mais)),
    PassoDoTour(Icone.Pessoas, R.string.tour_familia_titulo, R.string.tour_familia_texto, listOf(AlvoDoTour.Familia, AlvoDoTour.Mais)),
    PassoDoTour(Icone.Busca, R.string.tour_busca_titulo, R.string.tour_busca_texto, listOf(AlvoDoTour.Busca)),
    PassoDoTour(Icone.Brilho, R.string.tour_assistente_titulo, R.string.tour_assistente_texto, listOf(AlvoDoTour.Assistente)),
)

/**
 * O tour aberto no [passo] (Tutorial do site). [alvo] é o que a moldura achou na tela para este
 * passo; nulo, o passo abre num diálogo no centro.
 */
data class TourAberto(
    val passo: Int,
    val irPara: (Int) -> Unit,
    val fechar: () -> Unit,
    val alvo: AlvoDoTour? = null,
) {
    val ultimo: Boolean get() = passo == PASSOS.lastIndex

    fun avancar() = if (ultimo) fechar() else irPara(passo + 1)

    /**
     * O primeiro alvo do passo que está na tela (alvoNaTela do site): no celular e no trilho, o
     * Planejar e a Família ficam no Mais; na gaveta, cada um tem o seu item.
     */
    fun naMoldura(forma: FormaDoMenu): TourAberto = copy(
        alvo = PASSOS[passo].alvos.firstOrNull { alvo ->
            when (alvo) {
                AlvoDoTour.Planejar, AlvoDoTour.Familia -> forma == FormaDoMenu.Gaveta
                AlvoDoTour.Mais -> forma != FormaDoMenu.Gaveta
                else -> true
            }
        },
    )
}

/**
 * Embrulha um elemento da moldura (a aba, o item do trilho ou da gaveta, o + e os ícones do topo):
 * quando o passo aponta para [alvo], o balão sai dele (D-118 substituído). Tocar fora fecha o tour,
 * como o fundo do site.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AncoraDoTour(alvo: AlvoDoTour?, tour: TourAberto?, lado: TooltipAnchorPosition, conteudo: @Composable () -> Unit) {
    if (alvo == null) return conteudo()
    val aqui = tour != null && tour.alvo == alvo
    val estado = rememberTooltipState(isPersistent = true)
    // show() espera até o balão fechar; trocar de passo cancela e fecha este.
    LaunchedEffect(aqui, tour?.passo) { if (aqui) estado.show() else estado.dismiss() }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(lado),
        tooltip = { if (aqui) BalaoDoTour(tour!!) },
        state = estado,
        onDismissRequest = { tour?.fechar() },
        // Sem o toque longo do tooltip comum: o balão só aparece pelo tour.
        enableUserInput = false,
        hasAction = true,
        content = conteudo,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TooltipScope.BalaoDoTour(tour: TourAberto) {
    val passo = PASSOS[tour.passo]
    RichTooltip(
        title = { TituloDoPasso(passo) },
        action = { BotoesDoTour(tour) },
        caretShape = TooltipDefaults.caretShape(),
    ) { TextoDoPasso(passo, tour) }
}

/** O passo sem alvo na tela (o "Bem-vindo"): um diálogo no centro, como o balão solto do site. */
@Composable
fun DialogoDoTour(tour: TourAberto) {
    val passo = PASSOS[tour.passo]
    AlertDialog(
        onDismissRequest = tour.fechar,
        icon = { Icon(painterResource(passo.icone.desenho), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(passo.titulo)) },
        text = { TextoDoPasso(passo, tour) },
        confirmButton = { TextButton(tour::avancar) { Text(stringResource(if (tour.ultimo) R.string.tour_concluir else R.string.tour_proximo)) } },
        dismissButton = { if (!tour.ultimo) TextButton(tour.fechar) { Text(stringResource(R.string.tour_pular)) } },
    )
}

@Composable
private fun TituloDoPasso(passo: PassoDoTour) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(passo.icone.desenho), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(passo.titulo))
    }
}

/** O texto e as dicas do site: o que fica no Mais, e onde rever o tour no último passo. */
@Composable
private fun TextoDoPasso(passo: PassoDoTour, tour: TourAberto) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(passo.texto))
        if (tour.alvo == AlvoDoTour.Mais) Text(stringResource(R.string.tour_dica_no_mais), style = MaterialTheme.typography.bodySmall)
        if (tour.ultimo) Text(stringResource(R.string.tour_dica_rever), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BotoesDoTour(tour: TourAberto) {
    Row {
        if (!tour.ultimo) TextButton(tour.fechar) { Text(stringResource(R.string.tour_pular)) }
        TextButton(tour::avancar) { Text(stringResource(if (tour.ultimo) R.string.tour_concluir else R.string.tour_proximo)) }
    }
}
