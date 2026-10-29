package br.com.piggu.feature.gastos

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import br.com.piggu.core.ui.AbaEmBreve

/** Os Gastos nativos chegam na A4; até lá, a aba leva aos Gastos do site. */
@Composable
fun GastosScreen() {
    AbaEmBreve(stringResource(R.string.gastos_titulo), "/gastos")
}
