package br.com.piggu.core.testing

import br.com.piggu.core.data.FilaDeGastos
import br.com.piggu.core.data.GastoAguardando
import br.com.piggu.core.model.NovoLancamento
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** A fila sem Room nem rede, para os testes das telas e as capturas: só guarda. */
class FilaNaMemoria(aguardando: List<GastoAguardando> = emptyList(), recusados: Int = 0) : FilaDeGastos {
    override val aguardando = MutableStateFlow(aguardando)
    override val recusados = MutableStateFlow(recusados)

    override suspend fun guardar(lancamento: NovoLancamento) = aguardando.update {
        it + GastoAguardando(requireNotNull(lancamento.idDoEnvio), lancamento, 0)
    }

    override suspend fun enviar(): Int = 0
    override suspend fun esquecerRecusados() { recusados.value = 0 }
    override suspend fun apagarDaPessoa() { aguardando.value = emptyList() }
}
