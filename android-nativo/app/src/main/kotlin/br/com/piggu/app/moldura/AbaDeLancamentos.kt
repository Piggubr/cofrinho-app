package br.com.piggu.app.moldura

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.piggu.app.R
import br.com.piggu.app.navegacao.Tela

private val TELAS_DOS_LANCAMENTOS = listOf(Tela.Gastos to R.string.tela_gastos, Tela.Receitas to R.string.tela_receitas)

/**
 * A aba Lançamentos (D-164): Gastos e Receitas, cada uma com a sua tela, trocadas pelo seletor no
 * topo. Trocar é ir para a outra aba, então o voltar do sistema leva ao Painel, como antes.
 */
@Composable
fun AbaDeLancamentos(atual: Tela, abrir: (Tela) -> Unit, conteudo: @Composable () -> Unit) {
    Column {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            TELAS_DOS_LANCAMENTOS.forEachIndexed { indice, (tela, rotulo) ->
                SegmentedButton(
                    selected = tela == atual,
                    onClick = { if (tela != atual) abrir(tela) },
                    shape = SegmentedButtonDefaults.itemShape(indice, TELAS_DOS_LANCAMENTOS.size),
                ) { Text(stringResource(rotulo)) }
            }
        }
        Box(Modifier.weight(1f)) { conteudo() }
    }
}
