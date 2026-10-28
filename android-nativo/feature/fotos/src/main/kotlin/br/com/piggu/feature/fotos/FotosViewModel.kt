package br.com.piggu.feature.fotos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.EscopoDoApp
import br.com.piggu.core.data.EstadoDaSessao
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.ehPremium
import br.com.piggu.core.data.mesAtual
import br.com.piggu.core.model.FotoDoFeed
import br.com.piggu.core.model.NovaFotoDoFeed
import br.com.piggu.core.model.PedidoDeLegenda
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeMidia
import br.com.piggu.core.network.mensagemDeErro
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** Espera depois da última letra antes de salvar a legenda. */
internal const val ESPERA_DA_LEGENDA_MS = 800L

/** O tamanho máximo da legenda (CaptionRequest). */
private const val MAXIMO_DA_LEGENDA = 300

data class EstadoDasFotos(
    /** aaaa-mm do mural aberto. */
    val mes: String,
    /** aaaa-mm de hoje: o seletor não passa dele. */
    val mesAtual: String,
    val premium: Boolean = false,
    val fotos: Carga<List<FotoDoFeed>> = Carga.Carregando,
    /** Quando foi guardado o que aparece, se a atualização falhou ("28 de out., 10:00"). */
    val guardadoEm: String? = null,
    val conectado: Boolean = true,
    /** O que a pessoa está escrevendo em cada legenda, por id da foto, até ser salvo. */
    val legendas: Map<String, String> = emptyMap(),
    val enviando: Boolean = false,
    val erro: MensagemDeErro? = null,
    /** A foto que espera o "Apagar" (o confirm do site). */
    val apagando: FotoDoFeed? = null,
) {
    val lista: List<FotoDoFeed> get() = (fotos as? Carga.Pronto)?.valor.orEmpty()

    /** A legenda no campo: a que está sendo escrita ou a salva. */
    fun legendaDe(foto: FotoDoFeed): String = legendas[foto.id] ?: foto.legenda.orEmpty()
}

/**
 * Fotos do mês (features/feed e FotosView do iOS): o mural da família, mês a mês. Enviar é do
 * Premium; ver, legendar e apagar, de todos (feed.spec.ts). Cada legenda é salva sozinha, um pouco
 * depois da última letra, sem botão de salvar.
 */
@HiltViewModel
class FotosViewModel internal constructor(
    private val midia: ServicoDeMidia,
    private val cache: CacheDeRespostas,
    sessao: Sessao,
    private val relogio: Clock,
    /** Onde a legenda espera e salva: sair da tela logo depois de escrever não perde o texto. */
    private val escopo: CoroutineScope,
    conectado: StateFlow<Boolean>,
) : ViewModel() {
    @Inject constructor(
        midia: ServicoDeMidia,
        cache: CacheDeRespostas,
        sessao: Sessao,
        relogio: Clock,
        @EscopoDoApp escopo: CoroutineScope,
        rede: MonitorDeRede,
    ) : this(midia, cache, sessao, relogio, escopo, rede.conectado)

    private val mes = MutableStateFlow(relogio.mesAtual())
    private val recarga = MutableStateFlow(0)
    private val estadoAtual = MutableStateFlow(EstadoDasFotos(mes = mes.value, mesAtual = mes.value))
    val estado: StateFlow<EstadoDasFotos> = estadoAtual.asStateFlow()

    /** A legenda esperando para ser salva, por id da foto. */
    private val legendando = ConcurrentHashMap<String, Job>()

    init {
        // O plano pode mudar com a tela aberta (assinou o Premium e voltou).
        viewModelScope.launch {
            sessao.estado.collect { s ->
                estadoAtual.update { it.copy(premium = (s as? EstadoDaSessao.Logado)?.usuario?.ehPremium == true) }
            }
        }
        viewModelScope.launch {
            combine(mes, recarga, conectado) { mes, _, conectado -> mes to conectado }.collectLatest { (mes, conectado) ->
                estadoAtual.update { it.copy(conectado = conectado) }
                cache.carregar("fotos/$mes", ListSerializer(FotoDoFeed.serializer())) { midia.listarFeed(mes) }.collect { carga ->
                    val guardadaEm = (carga as? Carga.Pronto)?.guardadaEm
                    estadoAtual.update { it.copy(fotos = carga, guardadoEm = guardadaEm?.let(::quandoGuardado)) }
                }
            }
        }
    }

    fun tentarDeNovo() = recarga.update { it + 1 }

    fun mudarMes(novo: String) {
        mes.value = novo
        estadoAtual.update { it.copy(mes = novo, erro = null) }
    }

    /** A foto da galeria ou da câmera: [abrir] dá um fluxo novo dela a cada chamada. */
    fun enviar(abrir: () -> InputStream?) {
        val e = estadoAtual.value
        // Sem Premium a tela mostra o convite no lugar dos botões; o backend recusaria (402).
        if (e.enviando || !e.premium) return
        estadoAtual.update { it.copy(enviando = true, erro = null) }
        viewModelScope.launch {
            val jpeg = withContext(Dispatchers.IO) {
                try {
                    reduzirFoto(abrir)
                } catch (_: IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            }
            if (jpeg == null) {
                estadoAtual.update { it.copy(enviando = false, erro = MensagemDeErro.DoApp(R.string.fotos_ilegivel)) }
                return@launch
            }
            try {
                val foto = midia.publicarNoFeed(NovaFotoDoFeed(e.mes, Base64.getEncoder().encodeToString(jpeg), "image/jpeg"))
                // Se a pessoa trocou de mês enquanto enviava, a foto aparece quando voltar.
                mudarLista { lista -> if (foto.mesKey == estadoAtual.value.mes) listOf(foto) + lista else lista }
                estadoAtual.update { it.copy(enviando = false) }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(enviando = false, erro = mensagemDeErro(falha)) }
            }
        }
    }

    /** Guarda o texto na hora e salva depois de [ESPERA_DA_LEGENDA_MS] sem escrever. */
    fun mudarLegenda(foto: FotoDoFeed, texto: String) {
        val legenda = texto.take(MAXIMO_DA_LEGENDA)
        estadoAtual.update { it.copy(legendas = it.legendas + (foto.id to legenda)) }
        legendando.remove(foto.id)?.cancel()
        legendando[foto.id] = escopo.launch {
            delay(ESPERA_DA_LEGENDA_MS)
            salvarLegenda(foto, legenda)
        }
    }

    fun pedirParaApagar(foto: FotoDoFeed) = estadoAtual.update { it.copy(apagando = foto) }
    fun desistir() = estadoAtual.update { it.copy(apagando = null) }

    fun confirmar() {
        val foto = estadoAtual.value.apagando ?: return
        estadoAtual.update { it.copy(apagando = null) }
        // A legenda que esperava iria para uma foto que não existe mais.
        legendando.remove(foto.id)?.cancel()
        viewModelScope.launch {
            try {
                midia.excluirDoFeed(foto.id)
                mudarLista { lista -> lista.filter { it.id != foto.id } }
                estadoAtual.update { it.copy(legendas = it.legendas - foto.id, erro = null) }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(falha)) }
            }
        }
    }

    private suspend fun salvarLegenda(foto: FotoDoFeed, legenda: String) {
        try {
            // Voltou ao texto que já estava salvo: nada a mandar.
            val atualizada = if (legenda == foto.legenda.orEmpty()) foto else midia.legendar(foto.id, PedidoDeLegenda(legenda))
            mudarLista { lista -> lista.map { if (it.id == atualizada.id) atualizada else it } }
            // Só tira o rascunho se a pessoa não escreveu mais nada enquanto salvava.
            estadoAtual.update { e -> e.copy(legendas = if (e.legendas[foto.id] == legenda) e.legendas - foto.id else e.legendas, erro = null) }
        } catch (falha: ErroDaApi) {
            estadoAtual.update { it.copy(erro = mensagemDeErro(falha)) }
        }
    }

    private fun mudarLista(mudar: (List<FotoDoFeed>) -> List<FotoDoFeed>) = estadoAtual.update { e ->
        val pronto = e.fotos as? Carga.Pronto ?: return@update e
        e.copy(fotos = pronto.copy(valor = mudar(pronto.valor)))
    }

    /** "28 de out., 10:00": quando o guardado foi gravado. */
    private fun quandoGuardado(milissegundos: Long): String =
        DateTimeFormatter.ofPattern("d 'de' MMM, HH:mm", Locale.forLanguageTag("pt-BR"))
            .format(Instant.ofEpochMilli(milissegundos).atZone(relogio.zone))
}
