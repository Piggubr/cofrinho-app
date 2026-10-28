package br.com.piggu.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.core.ui.HumorDoPorquinho
import br.com.piggu.core.ui.Porquinho
import kotlinx.coroutines.launch
import br.com.piggu.core.designsystem.R as Icones

/** Os eventos da tela de entrada, para a versão sem ViewModel (capturas e testes). */
data class EventosDoLogin(
    val trocarModo: (ModoDaEntrada) -> Unit = {},
    val mudarEmail: (String) -> Unit = {},
    val mudarSenha: (String) -> Unit = {},
    val mudarLembrar: (Boolean) -> Unit = {},
    val entrar: () -> Unit = {},
    val entrarComGoogle: () -> Unit = {},
    val marcarAceite: (Boolean) -> Unit = {},
    val criarContaComGoogle: () -> Unit = {},
    val voltarDoAceite: () -> Unit = {},
    val esqueciASenha: () -> Unit = {},
    val abrirTermos: () -> Unit = {},
    val abrirPrivacidade: () -> Unit = {},
)

/**
 * A tela ligada ao [LoginViewModel]. [cadastro] é o formulário de criar conta, que recebe o
 * callback de conta criada.
 */
@Composable
fun TelaDeLogin(
    esqueciASenha: () -> Unit,
    abrirTermos: () -> Unit,
    abrirPrivacidade: () -> Unit,
    cadastro: @Composable (aoCriar: (String) -> Unit) -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    // Entrou com a senha: oferece guardar no gerenciador de senhas do aparelho.
    LaunchedEffect(estado.senhaParaSalvar) {
        estado.senhaParaSalvar?.let { (email, senha) ->
            viewModel.senhaOferecida()
            oferecerSalvarASenha(contexto, email, senha)
        }
    }
    TelaDeLogin(
        estado,
        EventosDoLogin(
            trocarModo = viewModel::trocarModo,
            mudarEmail = viewModel::mudarEmail,
            mudarSenha = viewModel::mudarSenha,
            mudarLembrar = viewModel::mudarLembrar,
            entrar = viewModel::entrar,
            entrarComGoogle = { escopo.launch { viewModel.aoVoltarDoGoogle(pedirOGoogle(contexto)) } },
            marcarAceite = viewModel::marcarAceite,
            criarContaComGoogle = viewModel::criarContaComGoogle,
            voltarDoAceite = viewModel::voltarDoAceite,
            esqueciASenha = esqueciASenha,
            abrirTermos = abrirTermos,
            abrirPrivacidade = abrirPrivacidade,
        ),
        mostrarGoogle = googleDisponivel,
        cadastro = { cadastro(viewModel::contaCriada) },
    )
}

/**
 * Entrada no app (features/login): as abas Entrar e Criar conta, o Google e "Continuar
 * conectado". Conta nova pelo Google troca o formulário pelo aceite dos termos.
 */
@Composable
fun TelaDeLogin(
    estado: EstadoDoLogin,
    eventos: EventosDoLogin,
    modifier: Modifier = Modifier,
    mostrarGoogle: Boolean = googleDisponivel,
    cadastro: @Composable () -> Unit = {},
) {
    Surface(modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Porquinho(HumorDoPorquinho.Feliz, largura = 120.dp)
            Text(
                stringResource(R.string.login_titulo),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.login_subtitulo), color = MaterialTheme.colorScheme.onSurfaceVariant)

            Column(Modifier.widthIn(max = 460.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Avisos(estado)
                if (estado.aguardandoAceite) {
                    AceiteDoGoogle(estado, eventos)
                } else {
                    val rotuloDasAbas = stringResource(R.string.login_entrar_ou_criar)
                    PrimaryTabRow(estado.modo.ordinal, Modifier.semantics { contentDescription = rotuloDasAbas }) {
                        Tab(estado.modo == ModoDaEntrada.Entrar, { eventos.trocarModo(ModoDaEntrada.Entrar) }, text = { Text(stringResource(R.string.login_entrar)) })
                        Tab(estado.modo == ModoDaEntrada.CriarConta, { eventos.trocarModo(ModoDaEntrada.CriarConta) }, text = { Text(stringResource(R.string.login_criar_conta)) })
                    }
                    when (estado.modo) {
                        ModoDaEntrada.Entrar -> FormularioDeEntrar(estado, eventos)
                        ModoDaEntrada.CriarConta -> cadastro()
                    }
                    if (mostrarGoogle) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HorizontalDivider(Modifier.weight(1f))
                            Text(stringResource(R.string.login_ou), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(Modifier.weight(1f))
                        }
                        OutlinedButton(eventos.entrarComGoogle, Modifier.fillMaxWidth(), enabled = !estado.entrando) {
                            Text(stringResource(R.string.login_google))
                        }
                    }
                    Lembrar(estado.lembrar, eventos.mudarLembrar)
                }
            }
        }
    }
}

@Composable
private fun Avisos(estado: EstadoDoLogin) {
    estado.erro?.let {
        Text(it.texto(LocalContext.current), Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, color = MaterialTheme.colorScheme.error)
    }
    if (estado.contaCriada) {
        Text(
            stringResource(R.string.cadastro_confira_seu_email, estado.email),
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (estado.entrando) {
        Text(stringResource(R.string.login_confirmando), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun FormularioDeEntrar(estado: EstadoDoLogin, eventos: EventosDoLogin) {
    var senhaVisivel by rememberSaveable { mutableStateOf(false) }
    val foco = LocalFocusManager.current
    OutlinedTextField(
        estado.email,
        eventos.mudarEmail,
        Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
        label = { Text(stringResource(R.string.login_email)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
    )
    OutlinedTextField(
        estado.senha,
        eventos.mudarSenha,
        Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
        label = { Text(stringResource(R.string.login_senha)) },
        singleLine = true,
        visualTransformation = if (senhaVisivel) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { foco.clearFocus(); eventos.entrar() }),
        trailingIcon = {
            IconButton({ senhaVisivel = !senhaVisivel }) {
                Icon(
                    painterResource(if (senhaVisivel) Icones.drawable.ic_olho_fechado else Icones.drawable.ic_olho),
                    stringResource(if (senhaVisivel) R.string.login_esconder_senha else R.string.login_mostrar_senha),
                )
            }
        },
    )
    Button({ foco.clearFocus(); eventos.entrar() }, Modifier.fillMaxWidth(), enabled = !estado.entrando) {
        Text(stringResource(R.string.login_entrar))
    }
    TextButton(eventos.esqueciASenha, Modifier.fillMaxWidth()) { Text(stringResource(R.string.login_esqueci)) }
}

/** Conta nova pelo Google (TERMOS_NECESSARIOS): o aceite antes de criar. */
@Composable
private fun AceiteDoGoogle(estado: EstadoDoLogin, eventos: EventosDoLogin) {
    Text(stringResource(R.string.login_falta_pouco), style = MaterialTheme.typography.titleMedium)
    Row(
        Modifier.fillMaxWidth().toggleable(estado.aceitou, role = Role.Checkbox, onValueChange = eventos.marcarAceite),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(estado.aceitou, onCheckedChange = null)
        Text(stringResource(R.string.login_li_e_aceito), Modifier.padding(start = 8.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(eventos.abrirTermos) { Text(stringResource(R.string.termos_de_uso)) }
        TextButton(eventos.abrirPrivacidade) { Text(stringResource(R.string.aviso_de_privacidade)) }
    }
    Button(eventos.criarContaComGoogle, Modifier.fillMaxWidth(), enabled = estado.aceitou && !estado.entrando) {
        Text(stringResource(R.string.login_criar_minha_conta))
    }
    TextButton(eventos.voltarDoAceite, Modifier.fillMaxWidth()) { Text(stringResource(R.string.voltar)) }
}

@Composable
private fun Lembrar(lembrar: Boolean, mudar: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(lembrar, role = Role.Checkbox, onValueChange = mudar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(lembrar, onCheckedChange = null)
        Text(stringResource(R.string.login_lembrar), Modifier.padding(start = 8.dp))
    }
}
