package br.com.piggu.core.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** O nome do trabalho único que envia a fila de gastos. */
const val ENVIO_DA_FILA = "enviar-fila-de-gastos"

/** Agenda o envio da fila para quando houver rede; o que já estava agendado continua (APPEND_OR_REPLACE). */
fun agendarEnvioDaFila(contexto: Context) {
    val pedido = OneTimeWorkRequestBuilder<EnvioDaFilaWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(contexto).enqueueUniqueWork(ENVIO_DA_FILA, ExistingWorkPolicy.APPEND_OR_REPLACE, pedido)
}

/**
 * Envia a fila ao abrir o app, quando a pessoa entra e quando o [MonitorDeRede] volta a ficar
 * conectado, além do agendamento feito ao guardar. O app chama [vigiar] uma vez, no
 * `PigguApplication.onCreate`.
 */
@Singleton
class EnvioDaFila @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val fila: FilaDeGastos,
    private val rede: MonitorDeRede,
    @EscopoDoApp private val escopo: CoroutineScope,
) {
    fun vigiar() {
        escopo.launch {
            combine(fila.aguardando, rede.conectado) { aguardando, conectado -> aguardando.isNotEmpty() to conectado }
                .distinctUntilChanged()
                .collect { (temGasto, _) -> if (temGasto) agendarEnvioDaFila(contexto) }
        }
    }
}

/**
 * Manda a fila quando o WorkManager vê rede. Num processo aberto só pelo WorkManager ninguém
 * restaura a sessão (é o app que faz isso, e duas renovações ao mesmo tempo derrubariam a
 * sessão): espera um pouco e, sem sessão decidida, tenta depois. O que sobrar (servidor fora)
 * também tenta depois, com o recuo do WorkManager.
 */
@HiltWorker
class EnvioDaFilaWorker @AssistedInject constructor(
    @Assisted contexto: Context,
    @Assisted parametros: WorkerParameters,
    private val fila: FilaNoRoom,
    private val sessao: Sessao,
) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result {
        withTimeoutOrNull(ESPERA_PELA_SESSAO) { sessao.estado.first { it !is EstadoDaSessao.Verificando } }
            ?: return Result.retry()
        fila.enviar()
        return if (fila.restam() > 0) Result.retry() else Result.success()
    }

    private companion object {
        const val ESPERA_PELA_SESSAO = 10_000L
    }
}
