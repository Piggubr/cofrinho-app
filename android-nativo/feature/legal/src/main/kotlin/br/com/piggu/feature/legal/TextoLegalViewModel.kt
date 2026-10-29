package br.com.piggu.feature.legal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** O que a tela mostra. Sem [texto] e sem [carregando], o arquivo não abriu. */
data class EstadoDoTextoLegal(
    val documento: DocumentoLegal,
    val carregando: Boolean = true,
    val texto: TextoLegal? = null,
) {
    val falhou: Boolean get() = !carregando && texto == null
}

/**
 * Lê o texto do documento ([ler] recebe o nome do arquivo em assets) e divide em seções. Sem
 * Hilt: o documento é um parâmetro, e a tela cria a VM com uma factory (viewModel { }).
 */
class TextoLegalViewModel(
    documento: DocumentoLegal,
    ler: (arquivo: String) -> String,
    despachante: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val estadoAtual = MutableStateFlow(EstadoDoTextoLegal(documento))
    val estado: StateFlow<EstadoDoTextoLegal> = estadoAtual.asStateFlow()

    init {
        viewModelScope.launch {
            val texto = withContext(despachante) {
                try {
                    lerTextoLegal(ler(documento.arquivo))
                } catch (_: IOException) {
                    null
                }
            }
            estadoAtual.value = estadoAtual.value.copy(carregando = false, texto = texto)
        }
    }
}
