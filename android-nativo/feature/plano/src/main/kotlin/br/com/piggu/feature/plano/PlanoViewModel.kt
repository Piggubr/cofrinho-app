package br.com.piggu.feature.plano

import android.app.Activity
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.ehAdmin
import br.com.piggu.core.model.InfoDoPlano
import br.com.piggu.core.model.PedidoDeCompraDoGooglePlay
import br.com.piggu.core.model.Plano
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeIdentidade
import br.com.piggu.core.network.mensagemDeErro
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
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
    /** O reembolso, a compra ou o restaurar em andamento: os botões esperam. */
    val ocupado: Boolean = false,
    /** O preço de cada assinatura no Google Play ("R$ 22,89"); vazio, a tela mostra o `app` do plano. */
    val precosDaLoja: Map<String, String> = emptyMap(),
    /** A assinatura do Google Play desta conta do Google, para o "Gerenciar assinatura". */
    val produtoDoPlay: String? = null,
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

    /** Só quem não tem Premium, nem pela casa, assina (uma assinatura por casa): ninguém paga duas vezes. */
    val podeAssinar: Boolean
        get() = info?.let { it.plano == Plano.GRATUITO && !admin && !it.casaTemAssinatura } == true
}

/**
 * Premium (features/plan e PremiumView do iOS): o plano de `/billing/plan` e o que cada plano
 * inclui, e a assinatura individual pelo Google Play (D-132). A compra só vira Premium depois
 * que o backend a aceita. O checkout e o portal da Stripe nunca abrem aqui, pela regra de
 * pagamentos do Google Play. O reembolso dos 7 dias (CDC art. 49) de quem assinou pelo site
 * continua, como no site.
 */
@HiltViewModel
class PlanoViewModel internal constructor(
    private val identidade: ServicoDeIdentidade,
    private val cache: CacheDeRespostas,
    private val sessao: Sessao,
    private val relogio: Clock,
    private val loja: LojaDoGooglePlay,
    conectado: StateFlow<Boolean>,
) : ViewModel() {
    @Inject constructor(
        identidade: ServicoDeIdentidade,
        cache: CacheDeRespostas,
        sessao: Sessao,
        relogio: Clock,
        loja: LojaDoGooglePlay,
        rede: MonitorDeRede,
    ) : this(identidade, cache, sessao, relogio, loja, rede.conectado)

    private val recarga = MutableStateFlow(0)
    private val estadoAtual = MutableStateFlow(EstadoDoPlano(admin = sessao.usuario?.ehAdmin == true))
    val estado: StateFlow<EstadoDoPlano> = estadoAtual.asStateFlow()
    private val primeiraCarga = CompletableDeferred<Unit>()

    init {
        viewModelScope.launch {
            combine(recarga, conectado) { _, conectado -> conectado }.collectLatest { conectado ->
                estadoAtual.update { it.copy(conectado = conectado) }
                cache.carregar("plano", InfoDoPlano.serializer()) { identidade.plano() }.collect { carga ->
                    val guardadaEm = (carga as? Carga.Pronto)?.guardadaEm
                    estadoAtual.update { it.copy(plano = carga, guardadoEm = guardadaEm?.let(::quandoGuardado)) }
                }
                primeiraCarga.complete(Unit)
            }
        }
        viewModelScope.launch {
            val precos = loja.precos()
            estadoAtual.update { it.copy(precosDaLoja = precos) }
        }
        // Depois do /billing/plan, para a resposta dele não passar por cima do plano que a compra trouxe.
        viewModelScope.launch {
            primeiraCarga.await()
            enviarAsQueFaltam()
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

    /** Abre a folha do Google Play; o Premium só aparece com a resposta do backend. */
    fun assinar(atividade: Activity, produto: String) {
        val conta = sessao.usuario?.id ?: return
        if (!comecar()) return
        viewModelScope.launch {
            when (val resultado = loja.comprar(atividade, produto, conta)) {
                is ResultadoDaCompra.Comprada -> {
                    val falha = enviar(listOf(resultado.compra))
                    terminar(aviso = R.string.plano_assinado.takeIf { falha == null }, erro = falha?.let(::mensagemDaCompra))
                }
                ResultadoDaCompra.Pendente -> terminar(aviso = R.string.plano_pagamento_pendente)
                ResultadoDaCompra.Cancelada -> terminar()
                ResultadoDaCompra.Falhou -> terminar(erro = MensagemDeErro.DoApp(R.string.plano_loja_falhou))
            }
        }
    }

    /** "Restaurar compras": manda ao backend cada assinatura ativa desta conta do Google. */
    fun restaurar() {
        if (!comecar()) return
        viewModelScope.launch {
            val compras = loja.compras()
            when {
                compras == null -> terminar(erro = MensagemDeErro.DoApp(R.string.plano_loja_falhou))
                compras.isEmpty() -> terminar(aviso = R.string.plano_nada_a_restaurar)
                else -> {
                    val falha = enviar(compras)
                    terminar(aviso = R.string.plano_restaurado.takeIf { falha == null }, erro = falha?.let(::mensagemDaCompra))
                }
            }
        }
    }

    /** Ao abrir, sem aviso: a compra que não chegou ao backend (sem internet, app fechado) vai agora. */
    private suspend fun enviarAsQueFaltam() {
        val compras = loja.compras() ?: return
        estadoAtual.update { it.copy(produtoDoPlay = compras.firstOrNull()?.produto) }
        // Reconhecida é a que o backend já recebeu: ele faz o acknowledge.
        compras.filterNot { it.reconhecida }.takeIf { it.isNotEmpty() }?.let { enviar(it) }
    }

    private fun comecar(): Boolean {
        if (estadoAtual.value.ocupado) return false
        estadoAtual.update { it.copy(ocupado = true, erro = null, aviso = null) }
        return true
    }

    private fun terminar(@StringRes aviso: Int? = null, erro: MensagemDeErro? = null) =
        estadoAtual.update { it.copy(ocupado = false, aviso = aviso, erro = erro) }

    /**
     * Manda cada compra ao backend e mostra o plano que ele responde; renova o token, que leva o
     * plano aos outros serviços. Devolve a falha quando nenhuma foi aceita.
     */
    private suspend fun enviar(compras: List<CompraDoGooglePlay>): ErroDaApi? {
        var plano: InfoDoPlano? = null
        var falha: ErroDaApi? = null
        for (compra in compras) {
            try {
                plano = identidade.compraDoGooglePlay(PedidoDeCompraDoGooglePlay(compra.produto, compra.token))
            } catch (erro: ErroDaApi) {
                falha = erro
            }
        }
        val novo = plano ?: return falha
        sessao.tokenNovo(sessao.acesso)
        estadoAtual.update { it.copy(plano = Carga.Pronto(novo), guardadoEm = null, produtoDoPlay = compras.first().produto) }
        return null
    }

    private fun mensagemDaCompra(falha: ErroDaApi): MensagemDeErro =
        if (falha is ErroDaApi.DoServidor && falha.codigo == "OUTRA_CONTA") MensagemDeErro.DoApp(R.string.plano_outra_conta) else mensagemDeErro(falha)

    /** "28 de out., 10:00", como o AvisoDeGuardado do iOS. */
    private fun quandoGuardado(milissegundos: Long): String =
        DateTimeFormatter.ofPattern("d 'de' MMM, HH:mm", Locale.forLanguageTag("pt-BR"))
            .format(Instant.ofEpochMilli(milissegundos).atZone(relogio.zone))
}

/** As assinaturas do Google Play, já na do Piggu quando se sabe qual é. */
internal fun enderecoDaAssinatura(produto: String?, pacote: String): String =
    "https://play.google.com/store/account/subscriptions?" + (produto?.let { "sku=$it&" } ?: "") + "package=$pacote"
