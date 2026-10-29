package br.com.piggu.feature.gastos

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import br.com.piggu.core.ui.TelaVazia

@Composable
fun GastosScreen() {
    TelaVazia(stringResource(R.string.gastos_titulo), stringResource(R.string.gastos_em_breve))
}
