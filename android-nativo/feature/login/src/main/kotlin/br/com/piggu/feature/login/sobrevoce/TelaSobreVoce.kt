package br.com.piggu.feature.login.sobrevoce

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.feature.login.R
import br.com.piggu.feature.login.cadastro.CampoDeCelular
import br.com.piggu.feature.login.cadastro.CampoDeCpf
import br.com.piggu.feature.login.cadastro.CampoDeNascimento
import br.com.piggu.feature.login.cadastro.Enviando
import br.com.piggu.feature.login.cadastro.ErroNoTopo
import br.com.piggu.feature.login.cadastro.MolduraDaEntrada
import br.com.piggu.feature.login.cadastro.textoDeApoio

/**
 * "Sobre você" (features/sobre-voce), a pendência de quem entrou pelo Google ou tem conta antiga.
 * Ao salvar, a sessão muda e o app sai daqui sozinho.
 *
 * @param sair encerra a sessão (o botão "Sair" do site).
 */
@Composable
fun TelaSobreVoce(sair: () -> Unit, viewModel: SobreVoceViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val foco = LocalFocusManager.current
    val focos = remember { CampoSobreVoce.entries.associateWith { FocusRequester() } }
    LaunchedEffect(estado.tentativas) {
        estado.campoComProblema?.let { focos.getValue(it).requestFocus() }
    }
    fun erroDe(campo: CampoSobreVoce) = estado.erro.takeIf { estado.campoComProblema == campo }

    MolduraDaEntrada(
        stringResource(R.string.sobre_voce_titulo),
        stringResource(if (estado.pedeCpf || estado.pedeCelular) R.string.sobre_voce_complete else R.string.sobre_voce_como_chamar),
    ) {
        if (estado.campoComProblema == null) ErroNoTopo(estado.erro)
        Enviando(estado.enviando)
        OutlinedTextField(
            estado.apelido,
            viewModel::mudarApelido,
            Modifier.fillMaxWidth().focusRequester(focos.getValue(CampoSobreVoce.Apelido))
                .semantics { contentType = ContentType.PersonFirstName },
            label = { Text(stringResource(R.string.cadastro_nome)) },
            singleLine = true,
            isError = erroDe(CampoSobreVoce.Apelido) != null,
            supportingText = textoDeApoio(erroDe(CampoSobreVoce.Apelido)),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        if (estado.pedeNascimento) {
            CampoDeNascimento(
                estado.nascimento,
                viewModel::mudarNascimento,
                erroDe(CampoSobreVoce.Nascimento),
                focos.getValue(CampoSobreVoce.Nascimento),
                stringResource(R.string.sobre_voce_nota_do_nascimento),
            )
        }
        if (estado.pedeCelular) {
            CampoDeCelular(
                estado.paisDoDdi,
                viewModel::mudarPaisDoDdi,
                estado.telefone,
                viewModel::mudarTelefone,
                erroDe(CampoSobreVoce.Telefone),
                focos.getValue(CampoSobreVoce.Telefone),
            )
        }
        if (estado.pedeCpf) {
            CampoDeCpf(
                estado.cpf,
                viewModel::mudarCpf,
                erroDe(CampoSobreVoce.Cpf),
                focos.getValue(CampoSobreVoce.Cpf),
                stringResource(R.string.sobre_voce_dica_do_cpf),
            )
        }
        Button({ foco.clearFocus(); viewModel.continuar() }, Modifier.fillMaxWidth(), enabled = !estado.enviando) {
            Text(stringResource(R.string.sobre_voce_continuar))
        }
        OutlinedButton(sair, Modifier.fillMaxWidth(), enabled = !estado.enviando) {
            Text(stringResource(R.string.sobre_voce_sair))
        }
    }
}
