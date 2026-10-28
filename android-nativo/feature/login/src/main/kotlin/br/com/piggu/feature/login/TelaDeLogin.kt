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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import br.com.piggu.core.designsystem.R as Icones
import br.com.piggu.core.ui.HumorDoPorquinho
import br.com.piggu.core.ui.Porquinho

/** O que a tela de login mostra. [erro] já vem pronto para ler (texto do servidor ou um dos nossos). */
data class EstadoDoLogin(
    val email: String = "",
    val senha: String = "",
    val lembrar: Boolean = true,
    val entrando: Boolean = false,
    val erro: String? = null,
)

/**
 * Entrar por e-mail e senha (features/login do site). Criar conta e esqueci a senha abrem no
 * site até a A3, que traz a entrada inteira, com o Google.
 */
@Composable
fun TelaDeLogin(
    estado: EstadoDoLogin,
    mudarEmail: (String) -> Unit,
    mudarSenha: (String) -> Unit,
    mudarLembrar: (Boolean) -> Unit,
    entrar: () -> Unit,
    esqueciASenha: () -> Unit,
    criarConta: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var senhaVisivel by rememberSaveable { mutableStateOf(false) }
    val foco = LocalFocusManager.current
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
            Porquinho(HumorDoPorquinho.Feliz, largura = 140.dp)
            Text(
                stringResource(R.string.login_titulo),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.login_subtitulo), color = MaterialTheme.colorScheme.onSurfaceVariant)

            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                estado.erro?.let {
                    Text(
                        it,
                        Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (estado.entrando) {
                    Text(stringResource(R.string.login_confirmando), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                OutlinedTextField(
                    estado.email,
                    mudarEmail,
                    Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
                    label = { Text(stringResource(R.string.login_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    estado.senha,
                    mudarSenha,
                    Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
                    label = { Text(stringResource(R.string.login_senha)) },
                    singleLine = true,
                    visualTransformation = if (senhaVisivel) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { foco.clearFocus(); entrar() }),
                    trailingIcon = {
                        IconButton({ senhaVisivel = !senhaVisivel }) {
                            Icon(
                                painterResource(if (senhaVisivel) Icones.drawable.ic_olho_fechado else Icones.drawable.ic_olho),
                                stringResource(if (senhaVisivel) R.string.login_esconder_senha else R.string.login_mostrar_senha),
                            )
                        }
                    },
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(estado.lembrar, role = Role.Checkbox, onValueChange = mudarLembrar),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(estado.lembrar, onCheckedChange = null)
                    Text(stringResource(R.string.login_lembrar), Modifier.padding(start = 8.dp))
                }
                Button({ foco.clearFocus(); entrar() }, Modifier.fillMaxWidth(), enabled = !estado.entrando) {
                    Text(stringResource(R.string.login_entrar))
                }
                TextButton(esqueciASenha, Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.login_esqueci))
                }
                TextButton(criarConta, Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.login_criar_conta))
                }
            }
        }
    }
}
