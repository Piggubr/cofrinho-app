package br.com.piggu.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** A visão dos números, com o valor que a API recebe em `visao` (Visao de models.ts). */
enum class Visao(val id: String) { Tudo("tudo"), Meu("meu"), Compartilhado("casal") }

/**
 * Tudo, Meu e Compartilhado (o alternador do Painel do site). Só aparece para quem mora com
 * alguém: sozinho, as três dariam o mesmo número.
 */
@Composable
fun ChipsDeVisao(visao: Visao, aoMudar: (Visao) -> Unit, moraComAlguem: Boolean, modifier: Modifier = Modifier) {
    if (!moraComAlguem) return
    val oQueVer = stringResource(R.string.visao_o_que_ver)
    // Com a fonte grande ou na tela estreita, o chip que não cabe desce de linha em vez de quebrar a palavra.
    FlowRow(
        modifier.selectableGroup().semantics { contentDescription = oQueVer },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Visao.entries.forEach { opcao ->
            val rotulo = when (opcao) {
                Visao.Tudo -> R.string.visao_tudo
                Visao.Meu -> R.string.visao_meu
                Visao.Compartilhado -> R.string.visao_compartilhado
            }
            FilterChip(
                selected = visao == opcao,
                onClick = { aoMudar(opcao) },
                label = { Text(stringResource(rotulo)) },
                modifier = Modifier.semantics { role = Role.RadioButton },
            )
        }
    }
}
