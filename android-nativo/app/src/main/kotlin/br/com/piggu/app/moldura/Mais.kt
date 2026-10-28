package br.com.piggu.app.moldura

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.piggu.app.R
import br.com.piggu.app.navegacao.Tela
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.designsystem.R as Icones
import br.com.piggu.core.ui.R as RDaUi

/** clickable com o papel para o TalkBack ("botão"). */
fun Modifier.clickableComPapel(papel: Role, aoTocar: () -> Unit): Modifier = clickable(role = papel, onClick = aoTocar)

/** O ícone de "abre fora" dos itens que ainda abrem no site; o TalkBack lê "Abre no site do Piggu". */
@Composable
fun IconeAbreFora() {
    Icon(
        painterResource(Icones.drawable.ic_abre_fora),
        stringResource(RDaUi.string.abre_no_site),
        Modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A pessoa no topo do Mais e da gaveta: a inicial, o nome e o e-mail; abre o Perfil. */
@Composable
fun CartaoDoPerfil(perfil: PerfilDaMoldura, abrirPerfil: () -> Unit) {
    val dica = stringResource(R.string.abre_o_perfil)
    ListItem(
        headlineContent = { Text(perfil.nome, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(perfil.email) },
        leadingContent = { Inicial(perfil.nome) },
        trailingContent = { if (!Tela.Perfil.nativa) IconeAbreFora() },
        modifier = Modifier.clickable(onClickLabel = dica, role = Role.Button, onClick = abrirPerfil),
    )
}

/** Sem foto (o app ainda não baixa imagens): a primeira letra do nome, como o avatar do site. */
@Composable
private fun Inicial(nome: String) {
    Box(
        Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            nome.trim().take(1).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * A aba Mais (MaisView do iOS, folha do Mais do site): o Perfil no topo, o plano, os grupos do
 * menu sem as abas, os textos legais, o Sair com confirmação e a versão.
 */
@Composable
fun TelaDoMais(
    perfil: PerfilDaMoldura,
    versao: String,
    abrir: (Tela) -> Unit,
    sair: () -> Unit,
    folgaEmbaixo: PaddingValues,
    excluirConta: () -> Unit = {},
    avisoDoEmail: @Composable () -> Unit = {},
) {
    var confirmandoSaida by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = folgaEmbaixo) {
        item { CartaoDoPerfil(perfil) { abrir(Tela.Perfil) } }
        if (!perfil.emailConfirmado) item { Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { avisoDoEmail() } }
        if (!perfil.cpfInformado) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.cpf_nao_informado)) },
                    supportingContent = { Text(stringResource(R.string.cpf_informar_agora)) },
                    modifier = Modifier.clickableComPapel(Role.Button) { abrir(Tela.Cpf) },
                )
            }
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.plano)) },
                trailingContent = {
                    Text(stringResource(if (perfil.premium) R.string.plano_premium else R.string.plano_gratuito))
                },
            )
        }
        gruposVisiveis(perfil.ehTitular, perfil.modulos, semAsAbas = true).forEach { (grupo, itens) ->
            item { TituloDoGrupo(stringResource(grupo.titulo)) }
            itens.forEach { item ->
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(item.rotulo)) },
                        leadingContent = { Icon(painterResource(item.icone.desenho), null) },
                        trailingContent = { if (!item.tela.nativa) IconeAbreFora() },
                        modifier = Modifier.clickableComPapel(Role.Button) { abrir(item.tela) },
                    )
                }
            }
        }
        item { TituloDoGrupo(stringResource(R.string.termos_e_privacidade)) }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.termos_de_uso)) },
                modifier = Modifier.clickableComPapel(Role.Button) { abrir(Tela.Termos) },
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.aviso_de_privacidade)) },
                modifier = Modifier.clickableComPapel(Role.Button) { abrir(Tela.Privacidade) },
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.sair), color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(painterResource(Icone.Sair.desenho), null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickableComPapel(Role.Button) { confirmandoSaida = true },
            )
        }
        // O Google Play exige excluir a conta dentro do app; o Perfil do site tem o mesmo botão.
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.excluir_minha_conta), color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(painterResource(Icone.Lixeira.desenho), null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickableComPapel(Role.Button, excluirConta),
            )
        }
        item {
            Text(
                stringResource(R.string.versao, versao),
                Modifier.fillMaxWidth().padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
    if (confirmandoSaida) {
        AlertDialog(
            onDismissRequest = { confirmandoSaida = false },
            title = { Text(stringResource(R.string.sair_pergunta)) },
            confirmButton = {
                TextButton({ confirmandoSaida = false; sair() }) {
                    Text(stringResource(R.string.sair), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ confirmandoSaida = false }) { Text(stringResource(R.string.cancelar)) } },
        )
    }
}

@Composable
private fun TituloDoGrupo(titulo: String) {
    Text(
        titulo,
        Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}
