package br.com.piggu.core.data

import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.model.JsonDoPiggu
import br.com.piggu.core.network.ErroDaApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** O que uma tela tem para mostrar (Carga do iOS). */
sealed interface Carga<out T> {
    data object Carregando : Carga<Nothing>

    /** [guardadaEm] (milissegundos) vem quando a atualização falhou e o valor é o do aparelho. */
    data class Pronto<T>(val valor: T, val guardadaEm: Long? = null) : Carga<T>

    data class Falhou(val erro: ErroDaApi) : Carga<Nothing>
}

/**
 * Offline-first: mostra o guardado no Room na hora, busca na rede e, dando certo, guarda e mostra
 * o novo. Sem rede, fica o guardado com a hora dele, ou o erro se não havia nada.
 */
@Singleton
class CacheDeRespostas @Inject constructor(
    private val respostas: RespostasGuardadasDao,
    private val relogio: Clock,
) {
    fun <T> carregar(chave: String, serializador: KSerializer<T>, buscar: suspend () -> T): Flow<Carga<T>> = flow {
        val guardada = respostas.ler(chave)
        val guardado = guardada?.let { runCatching { JsonDoPiggu.decodeFromString(serializador, it.json) }.getOrNull() }
        emit(if (guardado != null) Carga.Pronto(guardado) else Carga.Carregando)
        try {
            val novo = buscar()
            respostas.guardar(RespostaGuardada(chave, JsonDoPiggu.encodeToString(serializador, novo), relogio.millis()))
            emit(Carga.Pronto(novo))
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (erro: ErroDaApi) {
            // Sessão recusada não mostra o guardado: a pessoa vai voltar ao login.
            val recusada = erro is ErroDaApi.DoServidor && erro.status == 401
            emit(if (guardado != null && !recusada) Carga.Pronto(guardado, guardada.guardadaEm) else Carga.Falhou(erro))
        }
    }

    /** O guardado, sem ir à rede: as listas de lançar sem sinal. */
    suspend fun <T> ler(chave: String, serializador: KSerializer<T>): T? =
        respostas.ler(chave)?.let { runCatching { JsonDoPiggu.decodeFromString(serializador, it.json) }.getOrNull() }
}
