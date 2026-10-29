package br.com.piggu.feature.painel

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import br.com.piggu.core.ui.AbaEmBreve

/** O Painel nativo chega na A4; até lá, a aba leva ao Painel do site. */
@Composable
fun PainelScreen() {
    AbaEmBreve(stringResource(R.string.painel_titulo), "/painel")
}
