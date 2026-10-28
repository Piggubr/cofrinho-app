package br.com.piggu.feature.login

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.HumorDoPorquinho
import br.com.piggu.core.ui.formatos.pareceEmail
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------- Confirmar e-mail (features/confirmar-email) ----------

sealed interface EstadoDaConfirmacao {
    data object Confirmando : EstadoDaConfirmacao
    data object Confirmado : EstadoDaConfirmacao
    data class Falhou(val erro: MensagemDeErro) : EstadoDaConfirmacao
}

/** O destino do link do e-mail de confirmação: manda o token ao abrir e mostra o resultado. */
@HiltViewModel(assistedFactory = ConfirmarEmailViewModel.Fabrica::class)
class ConfirmarEmailViewModel @AssistedInject constructor(
    private val sessao: Sessao,
    @Assisted token: String,
) : ViewModel() {
    @AssistedFactory
    interface Fabrica {
        fun criar(token: String): ConfirmarEmailViewModel
    }

    private val estadoAtual = MutableStateFlow<EstadoDaConfirmacao>(EstadoDaConfirmacao.Confirmando)
    val estado: StateFlow<EstadoDaConfirmacao> = estadoAtual.asStateFlow()

    init {
        viewModelScope.launch {
            estadoAtual.value = try {
                sessao.confirmarEmail(token)
                EstadoDaConfirmacao.Confirmado
            } catch (falha: ErroDaApi) {
                EstadoDaConfirmacao.Falhou(mensagemDeErro(falha))
            }
        }
    }
}

@Composable
fun TelaDeConfirmarEmail(token: String, abrirOPiggu: () -> Unit) {
    val viewModel = hiltViewModel<ConfirmarEmailViewModel, ConfirmarEmailViewModel.Fabrica>(key = token) { it.criar(token) }
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    TelaDeConfirmarEmail(estado, abrirOPiggu)
}

@Composable
fun TelaDeConfirmarEmail(estado: EstadoDaConfirmacao, abrirOPiggu: () -> Unit) {
    val titulo = when (estado) {
        EstadoDaConfirmacao.Confirmando -> R.string.confirmar_confirmando
        EstadoDaConfirmacao.Confirmado -> R.string.confirmar_ok_titulo
        is EstadoDaConfirmacao.Falhou -> R.string.confirmar_erro_titulo
    }
    CaixaDaEntrada(
        stringResource(titulo),
        humor = if (estado is EstadoDaConfirmacao.Falhou) HumorDoPorquinho.Tonto else HumorDoPorquinho.Feliz,
    ) {
        when (estado) {
            EstadoDaConfirmacao.Confirmando -> CircularProgressIndicator()
            EstadoDaConfirmacao.Confirmado -> Text(stringResource(R.string.confirmar_ok_texto), color = MaterialTheme.colorScheme.onSurfaceVariant)
            is EstadoDaConfirmacao.Falhou -> TextoDeErro(estado.erro.texto(LocalContext.current))
        }
        if (estado != EstadoDaConfirmacao.Confirmando) {
            Button(abrirOPiggu, Modifier.fillMaxWidth()) { Text(stringResource(R.string.abrir_o_piggu)) }
        }
    }
}

// ---------- Reenviar a confirmação (o aviso do Perfil do site) ----------

data class EstadoDoReenvio(val enviando: Boolean = false, val enviado: Boolean = false, val erro: MensagemDeErro? = null)

@HiltViewModel
class ReenvioViewModel @Inject constructor(private val sessao: Sessao) : ViewModel() {
    private val estadoAtual = MutableStateFlow(EstadoDoReenvio())
    val estado: StateFlow<EstadoDoReenvio> = estadoAtual.asStateFlow()

    /** Manda outro link de confirmação; o anterior deixa de valer. */
    fun reenviar() {
        if (estadoAtual.value.enviando) return
        estadoAtual.update { EstadoDoReenvio(enviando = true) }
        viewModelScope.launch {
            estadoAtual.value = try {
                sessao.reenviarConfirmacao()
                EstadoDoReenvio(enviado = true)
            } catch (falha: ErroDaApi) {
                EstadoDoReenvio(erro = mensagemDeErro(falha))
            }
        }
    }
}

/** O aviso do Perfil do site para quem ainda não confirmou o e-mail, com o reenviar. */
@Composable
fun AvisoDeConfirmarEmail(email: String, modifier: Modifier = Modifier, viewModel: ReenvioViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    AvisoDeConfirmarEmail(email, estado, viewModel::reenviar, modifier)
}

@Composable
fun AvisoDeConfirmarEmail(email: String, estado: EstadoDoReenvio, reenviar: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.Card(
        modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        androidx.compose.foundation.layout.Column(
            Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.confirme_o_email_titulo), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.confirme_o_email_texto, email), style = MaterialTheme.typography.bodyMedium)
            when {
                estado.enviado -> TextoDeSucesso(stringResource(R.string.reenviado))
                else -> {
                    estado.erro?.let { TextoDeErro(it.texto(LocalContext.current)) }
                    androidx.compose.material3.OutlinedButton(reenviar, enabled = !estado.enviando) {
                        Text(stringResource(R.string.reenviar))
                    }
                }
            }
        }
    }
}

// ---------- Esqueci a senha (features/redefinir-senha/esqueci-senha) ----------

data class EstadoDoEsqueci(
    val email: String = "",
    val enviando: Boolean = false,
    val enviado: Boolean = false,
    val erro: MensagemDeErro? = null,
)

/** A resposta é a mesma com ou sem conta, então a tela também não diz se o e-mail existe. */
@HiltViewModel
class EsqueciSenhaViewModel @Inject constructor(private val sessao: Sessao) : ViewModel() {
    private val estadoAtual = MutableStateFlow(EstadoDoEsqueci())
    val estado: StateFlow<EstadoDoEsqueci> = estadoAtual.asStateFlow()

    fun mudarEmail(email: String) = estadoAtual.update { it.copy(email = email) }

    fun pedir() {
        val email = estadoAtual.value.email.trim()
        if (!pareceEmail(email)) {
            estadoAtual.update { it.copy(erro = MensagemDeErro.DoApp(R.string.esqueci_confira_o_email)) }
            return
        }
        estadoAtual.update { it.copy(enviando = true, erro = null) }
        viewModelScope.launch {
            try {
                sessao.esqueciSenha(email)
                estadoAtual.update { it.copy(enviando = false, enviado = true) }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(enviando = false, erro = mensagemDeErro(falha, R.string.esqueci_nao_pediu)) }
            }
        }
    }
}

@Composable
fun TelaDeEsqueciASenha(voltarParaEntrar: () -> Unit, viewModel: EsqueciSenhaViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    TelaDeEsqueciASenha(estado, viewModel::mudarEmail, viewModel::pedir, voltarParaEntrar)
}

@Composable
fun TelaDeEsqueciASenha(estado: EstadoDoEsqueci, mudarEmail: (String) -> Unit, pedir: () -> Unit, voltarParaEntrar: () -> Unit) {
    CaixaDaEntrada(
        stringResource(R.string.esqueci_titulo),
        subtitulo = if (estado.enviado) null else stringResource(R.string.esqueci_subtitulo),
    ) {
        if (estado.enviado) {
            TextoDeSucesso(stringResource(R.string.esqueci_enviado, estado.email.trim()))
            Button(voltarParaEntrar, Modifier.fillMaxWidth()) { Text(stringResource(R.string.voltar_para_entrar)) }
            return@CaixaDaEntrada
        }
        estado.erro?.let { TextoDeErro(it.texto(LocalContext.current)) }
        OutlinedTextField(
            estado.email,
            mudarEmail,
            Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
            label = { Text(stringResource(R.string.login_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
        )
        Button(pedir, Modifier.fillMaxWidth(), enabled = !estado.enviando) { Text(stringResource(R.string.esqueci_mandar)) }
        TextButton(voltarParaEntrar) { Text(stringResource(R.string.voltar_para_entrar)) }
    }
}

// ---------- Redefinir a senha (features/redefinir-senha/redefinir-senha) ----------

enum class EtapaDaSenhaNova { Formulario, Salva, LinkRuim }

data class EstadoDaSenhaNova(
    val senha: String = "",
    val confirmacao: String = "",
    val salvando: Boolean = false,
    val etapa: EtapaDaSenhaNova = EtapaDaSenhaNova.Formulario,
    val erro: MensagemDeErro? = null,
)

/** O destino do link de senha nova. Depois de trocar, todas as sessões da conta caem e a pessoa entra de novo. */
@HiltViewModel(assistedFactory = SenhaNovaViewModel.Fabrica::class)
class SenhaNovaViewModel @AssistedInject constructor(
    private val sessao: Sessao,
    @Assisted private val token: String,
) : ViewModel() {
    @AssistedFactory
    interface Fabrica {
        fun criar(token: String): SenhaNovaViewModel
    }

    private val estadoAtual = MutableStateFlow(
        EstadoDaSenhaNova(etapa = if (token.isBlank()) EtapaDaSenhaNova.LinkRuim else EtapaDaSenhaNova.Formulario),
    )
    val estado: StateFlow<EstadoDaSenhaNova> = estadoAtual.asStateFlow()

    fun mudarSenha(senha: String) = estadoAtual.update { it.copy(senha = senha) }
    fun mudarConfirmacao(confirmacao: String) = estadoAtual.update { it.copy(confirmacao = confirmacao) }

    fun salvar() {
        val atual = estadoAtual.value
        problemaDaSenhaNova(atual.senha)?.let { problema ->
            estadoAtual.update { it.copy(erro = MensagemDeErro.DoApp(problema)) }
            return
        }
        if (atual.senha != atual.confirmacao) {
            estadoAtual.update { it.copy(erro = MensagemDeErro.DoApp(R.string.redefinir_diferentes)) }
            return
        }
        estadoAtual.update { it.copy(salvando = true, erro = null) }
        viewModelScope.launch {
            try {
                sessao.redefinirSenha(token, atual.senha)
                estadoAtual.update { it.copy(salvando = false, etapa = EtapaDaSenhaNova.Salva) }
            } catch (falha: ErroDaApi) {
                val linkRuim = falha is ErroDaApi.DoServidor && falha.codigo == "LINK_INVALIDO"
                estadoAtual.update {
                    if (linkRuim) it.copy(salvando = false, etapa = EtapaDaSenhaNova.LinkRuim)
                    else it.copy(salvando = false, erro = mensagemDeErro(falha, R.string.redefinir_nao_salvou))
                }
            }
        }
    }
}

@Composable
fun TelaDeSenhaNova(token: String, entrar: () -> Unit, pedirOutroLink: () -> Unit) {
    val viewModel = hiltViewModel<SenhaNovaViewModel, SenhaNovaViewModel.Fabrica>(key = token) { it.criar(token) }
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    TelaDeSenhaNova(estado, viewModel::mudarSenha, viewModel::mudarConfirmacao, viewModel::salvar, entrar, pedirOutroLink)
}

@Composable
fun TelaDeSenhaNova(
    estado: EstadoDaSenhaNova,
    mudarSenha: (String) -> Unit,
    mudarConfirmacao: (String) -> Unit,
    salvar: () -> Unit,
    entrar: () -> Unit,
    pedirOutroLink: () -> Unit,
) {
    when (estado.etapa) {
        EtapaDaSenhaNova.Salva -> CaixaDaEntrada(stringResource(R.string.redefinir_ok_titulo), subtitulo = stringResource(R.string.redefinir_ok_texto)) {
            Button(entrar, Modifier.fillMaxWidth()) { Text(stringResource(R.string.login_entrar)) }
        }
        EtapaDaSenhaNova.LinkRuim -> CaixaDaEntrada(stringResource(R.string.redefinir_vencido_titulo), humor = HumorDoPorquinho.Tonto) {
            TextoDeErro(stringResource(R.string.redefinir_vencido_texto))
            Button(pedirOutroLink, Modifier.fillMaxWidth()) { Text(stringResource(R.string.redefinir_pedir_outro)) }
        }
        EtapaDaSenhaNova.Formulario -> CaixaDaEntrada(stringResource(R.string.redefinir_titulo), subtitulo = stringResource(R.string.redefinir_subtitulo)) {
            estado.erro?.let { TextoDeErro(it.texto(LocalContext.current)) }
            OutlinedTextField(
                estado.senha,
                mudarSenha,
                Modifier.fillMaxWidth().semantics { contentType = ContentType.NewPassword },
                label = { Text(stringResource(R.string.redefinir_senha_nova)) },
                supportingText = { Text(stringResource(R.string.redefinir_dica)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                estado.confirmacao,
                mudarConfirmacao,
                Modifier.fillMaxWidth().semantics { contentType = ContentType.NewPassword },
                label = { Text(stringResource(R.string.redefinir_repita)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
            Button(salvar, Modifier.fillMaxWidth(), enabled = !estado.salvando) { Text(stringResource(R.string.redefinir_salvar)) }
        }
    }
}
