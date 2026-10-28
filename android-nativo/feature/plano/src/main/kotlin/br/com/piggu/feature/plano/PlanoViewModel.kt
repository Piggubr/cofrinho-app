package br.com.piggu.feature.plano

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.ehAdmin
import br.com.piggu.core.model.InfoDoPlano
import br.com.piggu.core.model.Plano
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeIdentidade
import br.com.piggu.core.network.mensagemDeErro
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** O cartão marcado como "Seu plano" (QualPlano do site). */
enum class QualPlano { GRATUITO, PREMIUM, CASAL, FAMILIA }

data class EstadoDoPlano(
    val plano: Carga<InfoDoPlano> = Carga.Carregando,
    /** Quando foi guardado o que aparece, se a atualização falhou ("28 de out., 10:00"). */
    val guardadoEm: String? = null,
    val conectado: Boolean = true,
    /** O ADMIN usa tudo do Premium sem ter plano nenhum. */
    val admin: Boolean = false,
    /** O pedido de reembolso em andamento: o botão espera. */
    val ocupado: Boolean = false,
    /** O "confirm" do site antes de cancelar e pedir o reembolso. */
    val confirmandoReembolso: Boolean = false,
    @param:StringRes val aviso: Int? = null,
    val erro: MensagemDeErro? = null,
) {
    val info: InfoDoPlano? get() = (plano as? Carga.Pronto)?.valor

    /** Nulo para o administrador sem plano, como no site. */
    val seuPlano: QualPlano?
        get() {
            val info = info ?: return null
            return when {
                info.plano != Plano.PREMIUM -> QualPlano.GRATUITO.takeUnless { admin }
                info.planoFamilia -> QualPlano.FAMILIA
                info.planoCasal == true -> QualPlano.CASAL
                else -> QualPlano.PREMIUM
            }
        }

    /** Onde o site mostra "Assinar": o app mostra "Em breve" até a compra do Google Play (A8). */
    val podeAssinar: Boolean
        get() = info?.let { it.plano == Plano.GRATUITO && !admin && !it.casaTemAssinatura } == true
}

/**
 * Premium (features/plan e PremiumView do iOS): o plano de `/billing/plan` e o que cada plano
 * inclui, com os preços do app. Assinar pelo app chega com a compra do Google Play (A8); o
 * checkout e o portal da Stripe nunca abrem aqui, pela regra de pagamentos do Google Play.
 * O reembolso dos 7 dias (CDC art. 49) de quem assinou pelo site continua, como no site.
 */
@HiltViewModel
class PlanoViewModel internal constructor(
    private val identidade: ServicoDeIdentidade,
    private val cache: CacheDeRespostas,
    private val sessao: Sessao,
    private val relogio: Clock,
    conectado: StateFlow<Boolean>,
) : ViewModel() {
    @Inject constructor(
        identidade: ServicoDeIdentidade,
        cache: CacheDeRespostas,
        sessao: Sessao,
        relogio: Clock,
        rede: MonitorDeRede,
    ) : this(identidade, cache, sessao, relogio, rede.conectado)

    private val recarga = MutableStateFlow(0)
    private val estadoAtual = MutableStateFlow(EstadoDoPlano(admin = sessao.usuario?.ehAdmin == true))
    val estado: StateFlow<EstadoDoPlano> = estadoAtual.asStateFlow()

    init {
        viewModelScope.launch {
            combine(recarga, conectado) { _, conectado -> conectado }.collectLatest { conectado ->
                estadoAtual.update { it.copy(conectado = conectado) }
                cache.carregar("plano", InfoDoPlano.serializer()) { identidade.plano() }.collect { carga ->
                    val guardadaEm = (carga as? Carga.Pronto)?.guardadaEm
                    estadoAtual.update { it.copy(plano = carga, guardadoEm = guardadaEm?.let(::quandoGuardado)) }
                }
            }
        }
    }

    fun tentarDeNovo() = recarga.update { it + 1 }

    fun pedirReembolso() = estadoAtual.update { it.copy(confirmandoReembolso = true) }
    fun desistir() = estadoAtual.update { it.copy(confirmandoReembolso = false) }

    /** Cancela agora e devolve o que foi pago; renova o token, que leva o plano aos outros serviços. */
    fun confirmarReembolso() {
        if (estadoAtual.value.ocupado) return
        estadoAtual.update { it.copy(confirmandoReembolso = false, ocupado = true, erro = null, aviso = null) }
        viewModelScope.launch {
            try {
                val (reembolsado) = identidade.reembolso()
                sessao.tokenNovo(sessao.acesso)
                estadoAtual.update {
                    it.copy(ocupado = false, aviso = if (reembolsado) R.string.plano_reembolsado else R.string.plano_cancelado_no_teste)
                }
                tentarDeNovo()
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(ocupado = false, erro = mensagemDeErro(falha)) }
            }
        }
    }

    /** "28 de out., 10:00", como o AvisoDeGuardado do iOS. */
    private fun quandoGuardado(milissegundos: Long): String =
        DateTimeFormatter.ofPattern("d 'de' MMM, HH:mm", Locale.forLanguageTag("pt-BR"))
            .format(Instant.ofEpochMilli(milissegundos).atZone(relogio.zone))
}
