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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A [FilaDeGastos] no Room (tabela gastos_aguardando). Guardar já agenda o envio no WorkManager
 * ([agendarEnvioDaFila]), que espera a rede. Só um envio por vez.
 *
 * Como no iOS (D-090 de lá, D-123): sair não apaga a fila; cada pessoa só vê e envia os seus,
 * quando entrar de novo neste aparelho.
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

    private val todos: Flow<List<GastoAguardando>> = sessao.estado
        .map { (it as? EstadoDaSessao.Logado)?.usuario?.id }
        .distinctUntilChanged()
        .flatMapLatest { usuario -> if (usuario == null) flowOf(emptyList()) else dao.observar(usuario) }
        .map { lista -> lista.mapNotNull(::aguardandoDe) }

    override val aguardando: StateFlow<List<GastoAguardando>> =
        todos.map { lista -> lista.filter { it.motivo == null } }.stateIn(escopo, SharingStarted.Eagerly, emptyList())

    override val recusados: StateFlow<List<GastoAguardando>> =
        todos.map { lista -> lista.filter { it.motivo != null } }.stateIn(escopo, SharingStarted.Eagerly, emptyList())

    override suspend fun guardar(lancamento: NovoLancamento) {
        val usuario = sessao.usuario?.id ?: return
        val idDoEnvio = requireNotNull(lancamento.idDoEnvio) { "O gasto da fila precisa do idDoEnvio (D-073)." }
        dao.guardar(GastoGuardado(0, usuario, idDoEnvio, JsonDoPiggu.encodeToString(NovoLancamento.serializer(), lancamento), relogio.millis()))
        agendarEnvioDaFila(contexto)
    }

    override suspend fun enviar(): Int = trava.withLock {
        val usuario = sessao.usuario?.id ?: return@withLock 0
        var enviados = 0
        for (guardado in dao.daPessoa(usuario)) {
            val pedido = aguardandoDe(guardado)?.lancamento
            if (pedido != null) {
                try {
                    financas.lancarGastos(pedido)
                    enviados++
                } catch (erro: ErroDaApi) {
                    if (!recusado(erro)) break
                    // Fica guardado com o motivo: a pessoa vê qual gasto não entrou (BUG-010).
                    dao.recusar(guardado.ordem, (erro as ErroDaApi.DoServidor).mensagem.orEmpty())
                    continue
                }
            }
            dao.tirar(guardado.ordem)
        }
        if (enviados > 0) lancamentos.avisar()
        enviados
    }

    /**
     * Só 400 e 422 dizem que o gasto é inválido, e o 404 do Piggu que a conta ou o cartão foi
     * apagado (D-147): nunca vai passar. Sem rede, servidor fora, sessão caída ou 404 de proxy
     * (sem o código): depois (D-075).
     */
    private fun recusado(erro: ErroDaApi) = erro is ErroDaApi.DoServidor &&
        (erro.status == 400 || erro.status == 422 || erro.status == 404 && erro.codigo == "NotFoundException")

    /** Quantos da pessoa logada ainda esperam, lido do banco (o Worker decide se tenta de novo). */
    suspend fun restam(): Int = sessao.usuario?.id?.let { dao.daPessoa(it).size } ?: 0

    override suspend fun dispensar(idDoEnvio: String) {
        val usuario = sessao.usuario?.id ?: return
        dao.dispensar(usuario, idDoEnvio)
    }

    // JSON que não lê mais (modelo mudou de um jeito incompatível) sai da fila no envio, como no iOS.
    private fun aguardandoDe(guardado: GastoGuardado): GastoAguardando? =
        runCatching { JsonDoPiggu.decodeFromString(NovoLancamento.serializer(), guardado.json) }.getOrNull()
            ?.let { GastoAguardando(guardado.idDoEnvio, it, guardado.guardadoEm, guardado.recusadoPor) }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ModuloDaFila {
    @Binds
    abstract fun fila(fila: FilaNoRoom): FilaDeGastos
}
