package br.com.piggu.core.data

import br.com.piggu.core.model.NovoLancamento
import kotlinx.coroutines.flow.StateFlow

/** Um gasto lançado sem internet, esperando subir. [lancamento] já leva o idDoEnvio (D-073). */
data class GastoAguardando(
    val idDoEnvio: String,
    val lancamento: NovoLancamento,
    /** Milissegundos desde 1970. */
    val guardadoEm: Long,
)

/**
 * A fila dos gastos lançados sem internet (fila-de-gastos.ts e FilaDeGastos.swift): guardada no
 * Room, de cada pessoa, e enviada na ordem quando a rede volta. Só 400 e 422 tiram da fila, e
 * contam como recusados; qualquer outra falha para o envio e guarda o resto (D-075).
 */
interface FilaDeGastos {
    /** Os da pessoa logada, na ordem em que foram lançados. */
    val aguardando: StateFlow<List<GastoAguardando>>

    /** Quantos o servidor recusou desde o último aviso. */
    val recusados: StateFlow<Int>

    /** Guarda para enviar depois; o [lancamento] precisa ter o idDoEnvio. */
    suspend fun guardar(lancamento: NovoLancamento)

    /** Envia os da pessoa logada, na ordem, e devolve quantos subiram. */
    suspend fun enviar(): Int

    suspend fun esquecerRecusados()

    /** "Sair" (pelo botão): os da pessoa saem do aparelho. Sessão que só caiu não apaga. */
    suspend fun apagarDaPessoa()
}
