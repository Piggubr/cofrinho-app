package br.com.piggu.core.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import br.com.piggu.core.ui.formatos.escreverValor
import br.com.piggu.core.ui.formatos.lerValor
import br.com.piggu.core.ui.formatos.simboloDaMoeda
import java.math.BigDecimal

/**
 * Campo de dinheiro que aceita vírgula e ponto (campo-de-valor.ts, D-076). Enquanto a pessoa
 * digita, só lê ([lerValor]): reescrever o texto agora tiraria a vírgula recém-digitada. Ao sair
 * do campo, o valor aparece no padrão brasileiro (12,50). Texto que não é valor dá nulo.
 */
@Composable
fun CampoDeValor(
    valor: BigDecimal?,
    aoMudar: (BigDecimal?) -> Unit,
    moeda: String,
    modifier: Modifier = Modifier,
    rotulo: String = stringResource(R.string.valor_rotulo, simboloDaMoeda(moeda)),
    erro: String? = null,
) {
    var texto by rememberSaveable { mutableStateOf(escreverValor(valor)) }
    // Valor trocado por fora (limpar o formulário, link com valor): o texto acompanha.
    LaunchedEffect(valor) {
        if (lerValor(texto) != valor) texto = escreverValor(valor)
    }
    OutlinedTextField(
        texto,
        { novo -> texto = novo; aoMudar(lerValor(novo)) },
        modifier.onFocusChanged { foco -> if (!foco.isFocused) lerValor(texto)?.let { texto = escreverValor(it) } },
        label = { Text(rotulo) },
        singleLine = true,
        isError = erro != null,
        supportingText = erro?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
    )
}
