package br.com.piggu.feature.painel

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import br.com.piggu.core.ui.TelaVazia

@Composable
fun PainelScreen() {
    TelaVazia(stringResource(R.string.painel_titulo), stringResource(R.string.painel_em_breve))
}
