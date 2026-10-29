package br.com.piggu.feature.relatorios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.EstadoDaSessao
import br.com.piggu.core.data.Lancamentos
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.data.RepositorioDoMes
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.ehPremium
import br.com.piggu.core.data.mesAtual
import br.com.piggu.core.model.RelatorioDoAno
import br.com.piggu.core.model.ResumoDoMes
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.core.network.mensagemDeErro
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** O CSV dos gastos do mês, pronto para o compartilhamento do Android. */
class CsvDoMes(val nome: String, val bytes: ByteArray)

data class EstadoDeRelatorios(
    /** aaaa-mm */
    val mes: String,
    val mesAtual: String,
    val moeda: String? = null,
    val resumo: Carga<ResumoDoMes> = Carga.Carregando,
    /** Quando foi guardado o resumo que aparece, se a atualização falhou ("28 de out., 10:00"). */
    val guardadoEm: String? = null,
    val conectado: Boolean = true,
    val ehPremium: Boolean = false,
    /** Nulo sem Premium: aí o ano nem é pedido e vira convite (reports.ts). */
    val doAno: Carga<RelatorioDoAno>? = null,
    val tabelaDoMes: Boolean = false,
    val tabelaDoAno: Boolean = false,
    /** Índice (0 a 11) da coluna tocada no gráfico do ano, com a dica do mês. */
    val mesEmFoco: Int? = null,
    val exportando: Boolean = false,
    val csv: CsvDoMes? = null,
    val erro: MensagemDeErro? = null,
) {
    val ano: Int get() = mes.take(4).toInt()

    val resumoPronto: ResumoDoMes? get() = (resumo as? Carga.Pronto)?.valor
    val anoPronto: RelatorioDoAno? get() = (doAno as? Carga.Pronto)?.valor

    /** Maior valor de categoria no mês, contando o anterior: a escala das barras (maiorDaCategoria do site). */
    val maiorDaCategoria: BigDecimal
        get() = resumoPronto?.porCategoria.orEmpty().flatMap { listOf(it.total, it.anterior) }.fold(BigDecimal.ONE, BigDecimal::max)

    /** Maior receita ou gasto de um mês do ano: a altura cheia do gráfico. */
    val maiorDoAno: BigDecimal
        get() = anoPronto?.meses.orEmpty().flatMap { listOf(it.receitas, it.gastos) }.fold(BigDecimal.ONE, BigDecimal::max)

    fun fracaoDoMes(valor: BigDecimal): Float = (valor.toDouble() / maiorDaCategoria.toDouble()).toFloat().coerceIn(0f, 1f)

    fun fracaoDoAno(valor: BigDecimal): Float = (valor.toDouble() / maiorDoAno.toDouble()).toFloat().coerceIn(0f, 1f)

    /** Exportar só com rede: o CSV vem do servidor, como no site e no iOS. */
    val podeExportar: Boolean get() = conectado && !exportando
}

/**
 * A aba Relatórios (features/reports e RelatoriosView do iOS): o mês por categoria contra o
 * anterior, com a projeção (grátis), o ano mês a mês (Premium) e o CSV dos gastos do mês.
 * Recarrega quando muda o mês, quando algo é lançado ([Lancamentos]) e quando a internet volta.
 */
@HiltViewModel
class RelatoriosViewModel internal constructor(
    private val financas: ServicoDeFinancas,
    private val doMes: RepositorioDoMes,
    private val cache: CacheDeRespostas,
    sessao: Sessao,
    lancamentos: Lancamentos,
    private val relogio: Clock,
    conectado: StateFlow<Boolean>,
) : ViewModel() {
    @Inject constructor(
        financas: ServicoDeFinancas,
        doMes: RepositorioDoMes,
        cache: CacheDeRespostas,
        sessao: Sessao,
        lancamentos: Lancamentos,
        relogio: Clock,
        rede: MonitorDeRede,
    ) : this(financas, doMes, cache, sessao, lancamentos, relogio, rede.conectado)

    private val mes = MutableStateFlow(relogio.mesAtual())
    private val recarga = MutableStateFlow(0)
    private val estadoAtual = MutableStateFlow(EstadoDeRelatorios(mes = mes.value, mesAtual = mes.value))
    val estado: StateFlow<EstadoDeRelatorios> = estadoAtual.asStateFlow()

    init {
        val usuario = sessao.estado.map { (it as? EstadoDaSessao.Logado)?.usuario }
        viewModelScope.launch {
            usuario.collect { u -> estadoAtual.update { it.copy(moeda = u?.preferencias?.moeda, ehPremium = u?.ehPremium == true) } }
        }
        viewModelScope.launch {
            combine(mes, lancamentos.versao, recarga, conectado) { mes, _, _, conectado -> mes to conectado }
                .collectLatest { (mes, conectado) ->
                    estadoAtual.update { it.copy(conectado = conectado) }
                    doMes.resumo(mes).collect { carga ->
                        val guardadoEm = (carga as? Carga.Pronto)?.guardadaEm?.let { quandoGuardado(it, relogio.zone) }
                        estadoAtual.update { it.copy(resumo = carga, guardadoEm = guardadoEm) }
                    }
                }
        }
        // O ano só muda de pedido quando muda o ano, como trocarMes do site.
        val ano = mes.map { it.take(4).toInt() }.distinctUntilChanged()
        val premium = usuario.map { it?.ehPremium == true }.distinctUntilChanged()
        viewModelScope.launch {
            combine(ano, premium, lancamentos.versao, recarga, conectado) { ano, premium, _, _, _ -> ano to premium }
                .collectLatest { (ano, premium) ->
                    if (!premium) {
                        estadoAtual.update { it.copy(doAno = null, mesEmFoco = null) }
                        return@collectLatest
                    }
                    cache.carregar("relatorio-ano/$ano", RelatorioDoAno.serializer()) { financas.relatorioDoAno(ano) }.collect { carga ->
                        estadoAtual.update { it.copy(doAno = carga, mesEmFoco = it.mesEmFoco.takeIf { carga is Carga.Pronto }) }
                    }
                }
        }
    }

    fun mudarMes(novo: String) {
        mes.value = novo
        estadoAtual.update { it.copy(mes = novo, mesEmFoco = if (novo.take(4) == it.mes.take(4)) it.mesEmFoco else null) }
    }

    fun tentarDeNovo() = recarga.update { it + 1 }

    fun alternarTabelaDoMes() = estadoAtual.update { it.copy(tabelaDoMes = !it.tabelaDoMes) }
    fun alternarTabelaDoAno() = estadoAtual.update { it.copy(tabelaDoAno = !it.tabelaDoAno) }

    /** Toque numa coluna do ano; tocar de novo na mesma fecha a dica. */
    fun focarMes(indice: Int?) = estadoAtual.update { it.copy(mesEmFoco = indice.takeIf { i -> i != it.mesEmFoco }) }

    /** Os gastos do mês em CSV, do servidor, com o nome do site (importar-extrato.ts). */
    fun exportar() {
        if (!estadoAtual.value.podeExportar) return
        val mes = estadoAtual.value.mes
        estadoAtual.update { it.copy(exportando = true, erro = null) }
        viewModelScope.launch {
            try {
                // O corpo vem em streaming: ler é ir à rede, fora da thread principal.
                val bytes = withContext(Dispatchers.IO) { financas.exportarGastos(mes).use { it.bytes() } }
                estadoAtual.update { it.copy(csv = CsvDoMes("piggu-gastos-$mes.csv", bytes), exportando = false) }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(falha), exportando = false) }
            } catch (falha: IOException) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(ErroDaApi.de(falha)), exportando = false) }
            }
        }
    }

    fun csvEntregue() = estadoAtual.update { it.copy(csv = null) }
}

/** "28 de out., 10:00": quando o guardado foi gravado, como o AvisoDeGuardado do iOS. */
internal fun quandoGuardado(milissegundos: Long, zona: ZoneId): String =
    DateTimeFormatter.ofPattern("d 'de' MMM, HH:mm", Locale.forLanguageTag("pt-BR"))
        .format(Instant.ofEpochMilli(milissegundos).atZone(zona))
