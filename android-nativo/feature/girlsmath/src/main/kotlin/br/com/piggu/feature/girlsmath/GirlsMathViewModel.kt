package br.com.piggu.feature.girlsmath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.mesAtual
import br.com.piggu.core.model.ItemDoGirlsMath
import br.com.piggu.core.model.NovoItemDoGirlsMath
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeEstiloDeVida
import br.com.piggu.core.network.ServicoDeMidia
import br.com.piggu.core.network.mensagemDeErro
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Collections
import java.util.Locale
import javax.inject.Inject

/** O campo que barrou o "Guardar", para marcar como inválido. */
enum class CampoDoGirlsMath { Produto, Valor }

data class EstadoDoGirlsMath(
    /** aaaa-mm do placar "este mês". */
    val mes: String,
    /** aaaa-mm-dd: a data do que é guardado. */
    val hoje: String,
    val itens: Carga<List<ItemDoGirlsMath>> = Carga.Carregando,
    /** Quando foi guardado o que aparece, se a atualização falhou ("28 de out., 10:00"). */
    val guardadoEm: String? = null,
    val conectado: Boolean = true,
    val moeda: String = "EUR",
    val produto: String = "",
    val loja: String = "",
    val valor: BigDecimal? = null,
    /** A foto escolhida, já reduzida, em JPEG base64 (o que a API recebe). */
    val foto: String? = null,
    val lendoFoto: Boolean = false,
    /** As fotos dos itens já baixadas, por fotoAssetId, como vieram (na demo, SVG). */
    val miniaturas: Map<String, ByteArray> = emptyMap(),
    val salvando: Boolean = false,
    val faltou: CampoDoGirlsMath? = null,
    val erro: MensagemDeErro? = null,
    /** O item que espera o "Apagar" (o confirm do site). */
    val apagando: ItemDoGirlsMath? = null,
) {
    val lista: List<ItemDoGirlsMath> get() = (itens as? Carga.Pronto)?.valor.orEmpty()

    /** Quanto deixou de gastar no mês (totaisDoGirlsMath do site). */
    val noMes: BigDecimal get() = lista.filter { it.data.startsWith(mes) }.fold(BigDecimal.ZERO) { soma, item -> soma + item.valor }

    /** Quanto deixou de gastar desde sempre. */
    val total: BigDecimal get() = lista.fold(BigDecimal.ZERO) { soma, item -> soma + item.valor }
}

/**
 * Girls Math (features/girls-math e GirlsMathView do iOS): o que a pessoa viu, quis e não comprou,
 * e quanto isso deixou no bolso. Produto, loja, valor e uma foto opcional; só de quem lançou (o
 * backend filtra). Não vira gasto nem receita. Todo papel usa, o membro também, com ou sem foto.
 */
@HiltViewModel
class GirlsMathViewModel internal constructor(
    private val estiloDeVida: ServicoDeEstiloDeVida,
    private val midia: ServicoDeMidia,
    private val cache: CacheDeRespostas,
    sessao: Sessao,
    private val relogio: Clock,
    conectado: StateFlow<Boolean>,
) : ViewModel() {
    @Inject constructor(
        estiloDeVida: ServicoDeEstiloDeVida,
        midia: ServicoDeMidia,
        cache: CacheDeRespostas,
        sessao: Sessao,
        relogio: Clock,
        rede: MonitorDeRede,
    ) : this(estiloDeVida, midia, cache, sessao, relogio, rede.conectado)

    private val recarga = MutableStateFlow(0)
    private val estadoAtual = MutableStateFlow(
        EstadoDoGirlsMath(
            mes = relogio.mesAtual(),
            hoje = LocalDate.now(relogio).toString(),
            moeda = sessao.usuario?.preferencias?.moeda ?: "EUR",
        ),
    )
    val estado: StateFlow<EstadoDoGirlsMath> = estadoAtual.asStateFlow()

    /** As fotos já pedidas: cada uma baixa uma vez só. */
    private val pedidas = Collections.synchronizedSet(mutableSetOf<String>())

    init {
        viewModelScope.launch {
            combine(recarga, conectado) { _, conectado -> conectado }.collectLatest { conectado ->
                estadoAtual.update { it.copy(mes = relogio.mesAtual(), hoje = LocalDate.now(relogio).toString(), conectado = conectado) }
                cache.carregar("girls-math", ListSerializer(ItemDoGirlsMath.serializer())) { estiloDeVida.listarGirlsMath() }.collect { carga ->
                    val guardadaEm = (carga as? Carga.Pronto)?.guardadaEm
                    estadoAtual.update { it.copy(itens = carga, guardadoEm = guardadaEm?.let(::quandoGuardado)) }
                    if (carga is Carga.Pronto) baixarFotos(carga.valor)
                }
            }
        }
    }

    fun tentarDeNovo() = recarga.update { it + 1 }

    fun mudarProduto(produto: String) = estadoAtual.update { it.copy(produto = produto.take(120)) }
    fun mudarLoja(loja: String) = estadoAtual.update { it.copy(loja = loja.take(120)) }
    fun mudarValor(valor: BigDecimal?) = estadoAtual.update { it.copy(valor = valor) }

    /** A foto da galeria ou da câmera: [abrir] dá um fluxo novo dela a cada chamada. */
    fun escolherFoto(abrir: () -> InputStream?) {
        estadoAtual.update { it.copy(lendoFoto = true) }
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
            estadoAtual.update {
                if (jpeg == null) {
                    it.copy(lendoFoto = false, erro = MensagemDeErro.DoApp(R.string.girlsmath_foto_ilegivel))
                } else {
                    it.copy(lendoFoto = false, foto = Base64.getEncoder().encodeToString(jpeg), erro = null)
                }
            }
        }
    }

    fun removerFoto() = estadoAtual.update { it.copy(foto = null) }

    fun guardar() {
        val e = estadoAtual.value
        if (e.salvando) return
        val produto = e.produto.trim()
        val valor = e.valor
        when {
            produto.isEmpty() -> recusar(CampoDoGirlsMath.Produto, R.string.girlsmath_falta_produto)
            valor == null || valor.signum() <= 0 -> recusar(CampoDoGirlsMath.Valor, R.string.girlsmath_falta_valor)
            else -> {
                estadoAtual.update { it.copy(salvando = true, faltou = null, erro = null) }
                viewModelScope.launch {
                    try {
                        val item = estiloDeVida.criarNoGirlsMath(
                            NovoItemDoGirlsMath(
                                produto = produto,
                                loja = e.loja.trim().ifEmpty { null },
                                valor = valor,
                                data = e.hoje,
                                imageBase64 = e.foto,
                                mimeType = e.foto?.let { "image/jpeg" },
                            ),
                        )
                        mudarLista { listOf(item) + it }
                        estadoAtual.update { it.copy(produto = "", loja = "", valor = null, foto = null, salvando = false) }
                        baixarFotos(listOf(item))
                    } catch (falha: ErroDaApi) {
                        estadoAtual.update { it.copy(salvando = false, erro = mensagemDeErro(falha)) }
                    }
                }
            }
        }
    }

    fun pedirParaApagar(item: ItemDoGirlsMath) = estadoAtual.update { it.copy(apagando = item) }
    fun desistir() = estadoAtual.update { it.copy(apagando = null) }

    fun confirmar() {
        val item = estadoAtual.value.apagando ?: return
        estadoAtual.update { it.copy(apagando = null) }
        viewModelScope.launch {
            try {
                estiloDeVida.excluirDoGirlsMath(item.id)
                mudarLista { lista -> lista.filter { it.id != item.id } }
                estadoAtual.update { it.copy(erro = null) }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(falha)) }
            }
        }
    }

    /** As fotos vêm do piggu-media com o token; sem rede, fica o marcador e tenta na próxima carga. */
    private fun baixarFotos(itens: List<ItemDoGirlsMath>) {
        itens.filter { it.temFoto == true }.mapNotNull { it.fotoAssetId }.filter(pedidas::add).forEach { id ->
            viewModelScope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) { midia.baixarArquivo(id).use { it.bytes() } }
                    estadoAtual.update { it.copy(miniaturas = it.miniaturas + (id to bytes)) }
                } catch (_: ErroDaApi) {
                    pedidas.remove(id)
                } catch (_: IOException) {
                    pedidas.remove(id)
                }
            }
        }
    }

    private fun recusar(campo: CampoDoGirlsMath, texto: Int) =
        estadoAtual.update { it.copy(faltou = campo, erro = MensagemDeErro.DoApp(texto)) }

    private fun mudarLista(mudar: (List<ItemDoGirlsMath>) -> List<ItemDoGirlsMath>) = estadoAtual.update { e ->
        val pronto = e.itens as? Carga.Pronto ?: return@update e
        e.copy(itens = pronto.copy(valor = mudar(pronto.valor)))
    }

    /** "28 de out., 10:00": quando o guardado foi gravado. */
    private fun quandoGuardado(milissegundos: Long): String =
        DateTimeFormatter.ofPattern("d 'de' MMM, HH:mm", Locale.forLanguageTag("pt-BR"))
            .format(Instant.ofEpochMilli(milissegundos).atZone(relogio.zone))
}
