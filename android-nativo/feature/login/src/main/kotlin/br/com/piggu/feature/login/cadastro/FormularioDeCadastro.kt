package br.com.piggu.feature.login.cadastro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.core.designsystem.R as Icones
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.feature.login.R

/**
 * O formulário "Criar conta", para a aba da tela de login (o modo "cadastro" de login.ts): nome,
 * nascimento, e-mail, senha duas vezes, celular com DDI, país onde mora, CPF e o aceite. O
 * primeiro campo com problema ganha o foco e a mensagem.
 *
 * @param abrirTermos e [abrirPrivacidade] abrem os textos dos links do aceite.
 * @param aoCriar a conta foi criada: a tela de login volta para "Entrar" com este e-mail e o
 *   aviso `R.string.cadastro_confira_seu_email`. Não abre sessão.
 */
@Composable
fun FormularioDeCadastro(
    abrirTermos: () -> Unit,
    abrirPrivacidade: () -> Unit,
    aoCriar: (email: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CadastroViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val foco = LocalFocusManager.current
    val focos = remember { CampoDoCadastro.entries.associateWith { FocusRequester() } }
    var senhaVisivel by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(estado.tentativas) {
        estado.campoComProblema?.let { focos.getValue(it).requestFocus() }
    }
    LaunchedEffect(estado.criadaPara) {
        estado.criadaPara?.let {
            aoCriar(it)
            viewModel.avisado()
        }
    }
    fun erroDe(campo: CampoDoCadastro): MensagemDeErro? = estado.erro.takeIf { estado.campoComProblema == campo }
    fun criar() {
        foco.clearFocus()
        viewModel.criar()
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (estado.campoComProblema == null) ErroNoTopo(estado.erro)
        Enviando(estado.enviando)

        OutlinedTextField(
            estado.nome,
            viewModel::mudarNome,
            Modifier.fillMaxWidth().focusRequester(focos.getValue(CampoDoCadastro.Nome))
                .semantics { contentType = ContentType.PersonFirstName },
            label = { Text(stringResource(R.string.cadastro_nome)) },
            singleLine = true,
            isError = erroDe(CampoDoCadastro.Nome) != null,
            supportingText = textoDeApoio(erroDe(CampoDoCadastro.Nome)),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        CampoDeNascimento(
            estado.nascimento,
            viewModel::mudarNascimento,
            erroDe(CampoDoCadastro.Nascimento),
            focos.getValue(CampoDoCadastro.Nascimento),
        )
        OutlinedTextField(
            estado.email,
            viewModel::mudarEmail,
            Modifier.fillMaxWidth().focusRequester(focos.getValue(CampoDoCadastro.Email))
                .semantics { contentType = ContentType.EmailAddress },
            label = { Text(stringResource(R.string.cadastro_email)) },
            singleLine = true,
            isError = erroDe(CampoDoCadastro.Email) != null,
            supportingText = textoDeApoio(erroDe(CampoDoCadastro.Email)),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        val transformacao = if (senhaVisivel) VisualTransformation.None else PasswordVisualTransformation()
        val olho: @Composable () -> Unit = {
            IconButton({ senhaVisivel = !senhaVisivel }) {
                Icon(
                    painterResource(if (senhaVisivel) Icones.drawable.ic_olho_fechado else Icones.drawable.ic_olho),
                    stringResource(if (senhaVisivel) R.string.login_esconder_senha else R.string.login_mostrar_senha),
                )
            }
        }
        OutlinedTextField(
            estado.senha,
            viewModel::mudarSenha,
            Modifier.fillMaxWidth().focusRequester(focos.getValue(CampoDoCadastro.Senha))
                .semantics { contentType = ContentType.NewPassword },
            label = { Text(stringResource(R.string.cadastro_senha)) },
            singleLine = true,
            isError = erroDe(CampoDoCadastro.Senha) != null,
            supportingText = textoDeApoio(erroDe(CampoDoCadastro.Senha), stringResource(R.string.cadastro_dica_da_senha)),
            visualTransformation = transformacao,
            trailingIcon = olho,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            estado.confirmacao,
            viewModel::mudarConfirmacao,
            Modifier.fillMaxWidth().focusRequester(focos.getValue(CampoDoCadastro.Confirmacao))
                .semantics { contentType = ContentType.NewPassword },
            label = { Text(stringResource(R.string.cadastro_confirmacao)) },
            singleLine = true,
            isError = erroDe(CampoDoCadastro.Confirmacao) != null,
            supportingText = textoDeApoio(erroDe(CampoDoCadastro.Confirmacao)),
            visualTransformation = transformacao,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        )
        CampoDeCelular(
            estado.paisDoDdi,
            viewModel::mudarPaisDoDdi,
            estado.telefone,
            viewModel::mudarTelefone,
            erroDe(CampoDoCadastro.Telefone),
            focos.getValue(CampoDoCadastro.Telefone),
        )
        SeletorDePais(estado.pais, viewModel::mudarPais, Modifier.fillMaxWidth())
        CampoDeCpf(
            estado.documento,
            viewModel::mudarDocumento,
            erroDe(CampoDoCadastro.Documento),
            focos.getValue(CampoDoCadastro.Documento),
            stringResource(R.string.cadastro_dica_do_documento),
        )
        AceiteDosTermos(
            estado.aceitou,
            viewModel::mudarAceite,
            erroDe(CampoDoCadastro.Aceite),
            focos.getValue(CampoDoCadastro.Aceite),
            abrirTermos,
            abrirPrivacidade,
        )
        Button(::criar, Modifier.fillMaxWidth(), enabled = !estado.enviando) {
            Text(stringResource(R.string.cadastro_criar))
        }
    }
}

/** "Li e aceito os Termos de Uso e o Aviso de Privacidade.", com os dois links. */
@Composable
private fun AceiteDosTermos(
    aceitou: Boolean,
    aoMudar: (Boolean) -> Unit,
    erro: MensagemDeErro?,
    foco: FocusRequester,
    abrirTermos: () -> Unit,
    abrirPrivacidade: () -> Unit,
) {
    val termos = stringResource(R.string.cadastro_termos)
    val privacidade = stringResource(R.string.cadastro_privacidade)
    val frase = stringResource(R.string.cadastro_aceite, termos, privacidade)
    val estilo = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    val texto = buildAnnotatedString {
        append(frase)
        listOf(termos to abrirTermos, privacidade to abrirPrivacidade).forEach { (link, abrir) ->
            val inicio = frase.indexOf(link)
            if (inicio >= 0) addLink(LinkAnnotation.Clickable(link, estilo) { abrir() }, inicio, inicio + link.length)
        }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A caixa tem 48dp; o texto ao lado fica só com os links.
            Checkbox(aceitou, aoMudar, Modifier.focusRequester(foco).semantics { contentDescription = frase })
            Text(
                texto,
                Modifier.padding(start = 4.dp),
                color = if (erro != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        erro?.let {
            Text(
                it.texto(LocalContext.current),
                Modifier.padding(start = 16.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
