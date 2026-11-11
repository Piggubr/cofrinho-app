package br.com.piggu.core.data

import br.com.piggu.core.model.NovoLancamento
import kotlinx.coroutines.flow.StateFlow

/** Um gasto lançado sem internet, esperando subir. [lancamento] já leva o idDoEnvio (D-073). */
data class GastoAguardando(
    val idDoEnvio: String,
    val lancamento: NovoLancamento,
    /** Milissegundos desde 1970. */
    val guardadoEm: Long,
    /** Nulo enquanto espera subir; recusado, o porquê que o servidor deu (vazio se ele não disse). */
    val motivo: String? = null,
)

/**
 * A fila dos gastos lançados sem internet (fila-de-gastos.ts e FilaDeGastos.swift): guardada no
 * Room, de cada pessoa, e enviada na ordem quando a rede volta. Só 400 e 422 tiram da espera e
 * viram recusados, com o motivo, até a pessoa dispensar (D-196); qualquer outra falha para o
 * envio e guarda o resto (D-075).
 */
interface FilaDeGastos {
    /** Os da pessoa logada, na ordem em que foram lançados. */
    val aguardando: StateFlow<List<GastoAguardando>>

    /** Os que o servidor recusou, com o motivo, até a pessoa dispensar. */
    val recusados: StateFlow<List<GastoAguardando>>

    /** Guarda para enviar depois; o [lancamento] precisa ter o idDoEnvio. */
    suspend fun guardar(lancamento: NovoLancamento)

    /** Envia os da pessoa logada, na ordem, e devolve quantos subiram. */
    suspend fun enviar(): Int

    /** Tira um recusado do aviso e do aparelho. */
    suspend fun dispensar(idDoEnvio: String)

}
