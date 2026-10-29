package br.com.piggu.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** O nome digitado, limpo como no site: sem espaço nas pontas nem repetido. */
fun nomeDaCategoria(texto: String): String = texto.trim().replace(Regex("\\s+"), " ")

/** A categoria que já existe com esse nome, em qualquer caixa. */
fun categoriaExistente(categorias: List<String>, nome: String): String? =
    categorias.firstOrNull { it.equals(nome, ignoreCase = true) }

/**
 * Categoria com "+ Nova categoria…" na própria lista (campo-de-categoria.ts). Escolher a última
 * opção troca a lista por um campo de nome; ao criar, a categoria já fica escolhida. Nome que já
 * existe (em outra caixa) só é escolhido.
 *
 * @param valor a escolhida; vazia é "Automática" (só no gasto, [automatica]).
 * @param criar cria no servidor e devolve a lista nova, ou falha com a mensagem para a tela.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoDeCategoria(
    categorias: List<String>,
    valor: String,
    aoEscolher: (String) -> Unit,
    criar: suspend (String) -> Result<List<String>>,
    aoCriar: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    automatica: Boolean = true,
) {
    var aberta by remember { mutableStateOf(false) }
    var criando by rememberSaveable { mutableStateOf(false) }
    var nome by rememberSaveable { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    var salvando by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()
    val digiteONome = stringResource(R.string.categoria_digite_o_nome)
    val rotuloAutomatica = stringResource(R.string.categoria_automatica)

    fun confirmar() {
        if (salvando) return
        val limpo = nomeDaCategoria(nome)
        if (limpo.length < 2) {
            erro = digiteONome
            return
        }
        categoriaExistente(categorias, limpo)?.let {
            aoEscolher(it)
            criando = false
            return
        }
        // Já vale para um "Lançar" tocado enquanto o servidor responde.
        val anterior = valor
        aoEscolher(limpo)
        salvando = true
        escopo.launch {
            criar(limpo)
                .onSuccess { lista ->
                    aoCriar(lista)
                    aoEscolher(categoriaExistente(lista, limpo) ?: limpo)
                    criando = false
                }
                .onFailure { falha ->
                    aoEscolher(anterior)
                    erro = falha.message
                }
            salvando = false
        }
    }

    if (criando) {
        val foco = remember { FocusRequester() }
        LaunchedEffect(Unit) { foco.requestFocus() }
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                nome,
                { nome = it.take(50); erro = null },
                Modifier.fillMaxWidth().focusRequester(foco),
                label = { Text(stringResource(R.string.categoria_nome_da_nova)) },
                placeholder = { Text(stringResource(R.string.categoria_exemplo)) },
                singleLine = true,
                isError = erro != null,
                supportingText = erro?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirmar() }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button({ confirmar() }, enabled = !salvando) { Text(stringResource(R.string.categoria_criar)) }
                TextButton({ criando = false }) { Text(stringResource(R.string.cancelar)) }
            }
        }
        return
    }

    ExposedDropdownMenuBox(aberta, { aberta = it }, modifier) {
        OutlinedTextField(
            valor.ifEmpty { if (automatica) rotuloAutomatica else "" },
            {},
            Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            label = { Text(stringResource(R.string.categoria)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(aberta) },
        )
        ExposedDropdownMenu(aberta, { aberta = false }) {
            if (automatica) DropdownMenuItem({ Text(rotuloAutomatica) }, { aoEscolher(""); aberta = false })
            categorias.forEach { categoria ->
                DropdownMenuItem({ Text(categoria) }, { aoEscolher(categoria); aberta = false })
            }
            DropdownMenuItem(
                { Text(stringResource(R.string.categoria_nova)) },
                { aberta = false; nome = ""; erro = null; criando = true },
            )
        }
    }
}
