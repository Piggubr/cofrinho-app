package br.com.piggu.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Os três casos de features/erros: 404, 500 e a 500 sem internet, que diz isso em vez de culpar o servidor. */
enum class CasoDeErro { NaoEncontrada, Servidor, SemInternet }

/**
 * Tela inteira de erro, sem a moldura (tela-de-erro.html): o porquinho, o código, o texto e os
 * botões. Na 404, "Ir para o início" e "Voltar"; nas outras, "Tentar de novo" e "Ir para o início".
 */
@Composable
fun TelaDeErro(caso: CasoDeErro, irParaOInicio: () -> Unit, voltarOuTentar: () -> Unit, modifier: Modifier = Modifier) {
    val (titulo, texto) = when (caso) {
        CasoDeErro.NaoEncontrada -> R.string.erro_404_titulo to R.string.erro_404_texto
        CasoDeErro.Servidor -> R.string.erro_500_titulo to R.string.erro_500_texto
        CasoDeErro.SemInternet -> R.string.erro_sem_internet_titulo to R.string.erro_sem_internet_texto
    }
    Surface(modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Porquinho(if (caso == CasoDeErro.NaoEncontrada) HumorDoPorquinho.Perdido else HumorDoPorquinho.Tonto, largura = 200.dp)
            when (caso) {
                CasoDeErro.NaoEncontrada -> Codigo("404")
                CasoDeErro.Servidor -> Codigo("500")
                CasoDeErro.SemInternet -> Unit
            }
            Text(
                stringResource(titulo),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(texto),
                Modifier.widthIn(max = 420.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (caso == CasoDeErro.NaoEncontrada) {
                Button(irParaOInicio) { Text(stringResource(R.string.ir_para_o_inicio)) }
                OutlinedButton(voltarOuTentar) { Text(stringResource(R.string.voltar)) }
            } else {
                Button(voltarOuTentar) { Text(stringResource(R.string.tentar_de_novo)) }
                OutlinedButton(irParaOInicio) { Text(stringResource(R.string.ir_para_o_inicio)) }
            }
        }
    }
}

/** O código grande da tela; o TalkBack pula, como o aria-hidden do site. */
@Composable
private fun Codigo(codigo: String) {
    Text(
        codigo,
        Modifier.clearAndSetSemantics {},
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}
