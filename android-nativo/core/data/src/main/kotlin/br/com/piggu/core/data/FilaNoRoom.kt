package br.com.piggu.core.data

import android.content.Context
import br.com.piggu.core.database.GastoGuardado
import br.com.piggu.core.database.GastosAguardandoDao
import br.com.piggu.core.model.JsonDoPiggu
import br.com.piggu.core.model.NovoLancamento
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.ServicoDeFinancas
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A [FilaDeGastos] no Room (tabela gastos_aguardando). Guardar já agenda o envio no WorkManager
 * ([agendarEnvioDaFila]), que espera a rede. Só um envio por vez.
 *
 * Diferente do iOS (D-090 de lá): "Sair" pelo botão apaga a fila da pessoa ([apagarDaPessoa]); a
 * sessão que só caiu não apaga, e a pessoa envia quando entrar de novo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FilaNoRoom @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val dao: GastosAguardandoDao,
    private val financas: ServicoDeFinancas,
    private val sessao: Sessao,
    private val lancamentos: Lancamentos,
    private val relogio: Clock,
    @EscopoDoApp escopo: CoroutineScope,
) : FilaDeGastos {
    private val trava = Mutex()
    private val contagemDeRecusados = MutableStateFlow(0)
    override val recusados: StateFlow<Int> = contagemDeRecusados.asStateFlow()

    override val aguardando: StateFlow<List<GastoAguardando>> = sessao.estado
        .map { (it as? EstadoDaSessao.Logado)?.usuario?.id }
        .distinctUntilChanged()
        .flatMapLatest { usuario -> if (usuario == null) flowOf(emptyList()) else dao.observar(usuario) }
        .map { lista -> lista.mapNotNull(::aguardandoDe) }
        .stateIn(escopo, SharingStarted.Eagerly, emptyList())

    override suspend fun guardar(lancamento: NovoLancamento) {
        val usuario = sessao.usuario?.id ?: return
        val idDoEnvio = requireNotNull(lancamento.idDoEnvio) { "O gasto da fila precisa do idDoEnvio (D-073)." }
        dao.guardar(GastoGuardado(0, usuario, idDoEnvio, JsonDoPiggu.encodeToString(NovoLancamento.serializer(), lancamento), relogio.millis()))
        agendarEnvioDaFila(contexto)
    }

    override suspend fun enviar(): Int = trava.withLock {
        val usuario = sessao.usuario?.id ?: return@withLock 0
        var enviados = 0
        var recusadosAgora = 0
        for (guardado in dao.daPessoa(usuario)) {
            val pedido = aguardandoDe(guardado)?.lancamento
            if (pedido != null) {
                try {
                    financas.lancarGastos(pedido)
                    enviados++
                } catch (erro: ErroDaApi) {
                    // Só 400 e 422 dizem que o gasto é inválido; sem rede, servidor fora ou sessão caída: depois (D-075).
                    if (!(erro is ErroDaApi.DoServidor && (erro.status == 400 || erro.status == 422))) break
                    recusadosAgora++
                }
            }
            dao.tirar(guardado.ordem)
        }
        if (recusadosAgora > 0) contagemDeRecusados.update { it + recusadosAgora }
        if (enviados > 0) lancamentos.avisar()
        enviados
    }

    /** Quantos da pessoa logada ainda esperam, lido do banco (o Worker decide se tenta de novo). */
    suspend fun restam(): Int = sessao.usuario?.id?.let { dao.daPessoa(it).size } ?: 0

    override suspend fun esquecerRecusados() {
        contagemDeRecusados.value = 0
    }

    override suspend fun apagarDaPessoa() {
        sessao.usuario?.id?.let { dao.apagarDaPessoa(it) }
        contagemDeRecusados.value = 0
    }

    // JSON que não lê mais (modelo mudou de um jeito incompatível) sai da fila no envio, como no iOS.
    private fun aguardandoDe(guardado: GastoGuardado): GastoAguardando? =
        runCatching { JsonDoPiggu.decodeFromString(NovoLancamento.serializer(), guardado.json) }.getOrNull()
            ?.let { GastoAguardando(guardado.idDoEnvio, it, guardado.guardadoEm) }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ModuloDaFila {
    @Binds
    abstract fun fila(fila: FilaNoRoom): FilaDeGastos
}
