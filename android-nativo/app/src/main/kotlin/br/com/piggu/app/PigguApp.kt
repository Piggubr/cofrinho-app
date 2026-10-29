package br.com.piggu.app

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import br.com.piggu.core.ui.TelaVazia
import br.com.piggu.feature.gastos.GastosScreen
import br.com.piggu.feature.painel.PainelScreen
import kotlinx.serialization.Serializable
import br.com.piggu.core.designsystem.R as Icones

@Serializable data object Painel : NavKey
@Serializable data object Gastos : NavKey
@Serializable data object Relatorios : NavKey
@Serializable data object Mais : NavKey

enum class Aba(val chave: NavKey, @StringRes val rotulo: Int, @DrawableRes val icone: Int) {
    PAINEL(Painel, R.string.aba_painel, Icones.drawable.ic_painel),
    GASTOS(Gastos, R.string.aba_gastos, Icones.drawable.ic_gastos),
    RELATORIOS(Relatorios, R.string.aba_relatorios, Icones.drawable.ic_relatorios),
    MAIS(Mais, R.string.aba_mais, Icones.drawable.ic_mais),
}

/** Troca de aba deixando o Painel na base: o voltar de qualquer aba leva ao Painel e, de lá, sai do app. */
fun MutableList<NavKey>.irPara(aba: NavKey) {
    if (lastOrNull() == aba) return
    retainAll(listOf(Painel))
    if (aba != Painel) add(aba)
}

@Composable
fun PigguApp() {
    val pilha = rememberNavBackStack(Painel)
    // Barra embaixo no celular e trilho do lado no tablet, pelo WindowSizeClass.
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Aba.entries.forEach { aba ->
                item(
                    selected = pilha.lastOrNull() == aba.chave,
                    onClick = { pilha.irPara(aba.chave) },
                    // O rótulo ao lado já é lido pelo TalkBack; descrever o ícone repetiria.
                    icon = { Icon(painterResource(aba.icone), contentDescription = null) },
                    label = { Text(stringResource(aba.rotulo)) },
                )
            }
        },
    ) {
        NavDisplay(
            backStack = pilha,
            onBack = { pilha.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<Painel> { PainelScreen() }
                entry<Gastos> { GastosScreen() }
                entry<Relatorios> { TelaVazia(stringResource(R.string.aba_relatorios), stringResource(R.string.em_breve)) }
                entry<Mais> { TelaVazia(stringResource(R.string.aba_mais), stringResource(R.string.em_breve)) }
            },
        )
    }
}
