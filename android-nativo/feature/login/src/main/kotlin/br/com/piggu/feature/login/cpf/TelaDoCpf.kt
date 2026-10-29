package br.com.piggu.feature.login.cpf

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.feature.login.R
import br.com.piggu.feature.login.cadastro.CampoDeCpf
import br.com.piggu.feature.login.cadastro.Enviando
import br.com.piggu.feature.login.cadastro.ErroNoTopo
import br.com.piggu.feature.login.cadastro.MolduraDaEntrada
import br.com.piggu.feature.login.cadastro.SeletorDePais

/**
 * "Seu documento" (features/cpf): o país onde mora e o CPF, opcional; o Perfil oferece a quem
 * ainda não informou.
 *
 * @param voltar "Agora não", e também depois de gravar.
 */
@Composable
fun TelaDoCpf(voltar: () -> Unit, viewModel: CpfViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val foco = LocalFocusManager.current
    val focoDoCpf = remember { FocusRequester() }
    LaunchedEffect(estado.tentativas) {
        if (estado.erroNoCampo != null) focoDoCpf.requestFocus()
    }
    LaunchedEffect(estado.gravado) {
        if (estado.gravado) voltar()
    }

    MolduraDaEntrada(stringResource(R.string.cpf_titulo), stringResource(R.string.cpf_subtitulo)) {
        ErroNoTopo(estado.erro)
        Enviando(estado.enviando)
        SeletorDePais(estado.pais, viewModel::mudarPais, Modifier.fillMaxWidth())
        CampoDeCpf(estado.documento, viewModel::mudarDocumento, estado.erroNoCampo, focoDoCpf)
        Button({ foco.clearFocus(); viewModel.continuar() }, Modifier.fillMaxWidth(), enabled = !estado.enviando) {
            Text(stringResource(R.string.cpf_continuar))
        }
        Text(
            stringResource(R.string.cpf_nota),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(voltar, Modifier.fillMaxWidth(), enabled = !estado.enviando) {
            Text(stringResource(R.string.cpf_agora_nao))
        }
    }
}
