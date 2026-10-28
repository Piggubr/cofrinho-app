package br.com.piggu.feature.calendario

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.Lancamentos
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.mesAtual
import br.com.piggu.core.model.Gasto
import br.com.piggu.core.model.Nota
import br.com.piggu.core.model.NovaNota
import br.com.piggu.core.model.PedidoDeNome
import br.com.piggu.core.model.Receita
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.core.network.mensagemDeErro
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** O que o calendário busca de uma vez, guardado junto para abrir sem internet ("calendario/2026-10"). */
@Serializable
data class DadosDoCalendario(
    val notas: List<Nota> = emptyList(),
    val gastos: List<Gasto> = emptyList(),
    val receitas: List<Receita> = emptyList(),
    val categorias: List<String> = emptyList(),
)

/** A nota sendo escrita no cartão "Nova nota". */
data class NotaEmEdicao(
    val titulo: String = "",
    val texto: String = "",
    val comData: Boolean = true,
    /** aaaa-mm-dd */
    val data: String,
    val valor: BigDecimal? = null,
    val categoria: String = "",
) {
    /** Com valor, o backend lança um gasto junto (e apagar a nota apaga o gasto). */
    val viraGasto: Boolean get() = (valor ?: BigDecimal.ZERO).signum() > 0
}

data class EstadoDoCalendario(
    /** aaaa-mm da grade. */
    val mes: String,
    /** aaaa-mm-dd */
    val hoje: String,
    val dados: Carga<DadosDoCalendario> = Carga.Carregando,
    /** Quando foi guardado o que aparece, se a atualização falhou ("28 de out., 10:00"). */
    val guardadoEm: String? = null,
    val conectado: Boolean = true,
    val moeda: String = "EUR",
    /** aaaa-mm-dd do dia tocado na grade. */
    val diaEscolhido: String? = null,
    /** O dia que o dedo (ou o TalkBack) aponta no gráfico. */
    val diaNoGrafico: Int? = null,
    val nova: NotaEmEdicao = NotaEmEdicao(data = hoje),
    val faltouTitulo: Boolean = false,
    val guardando: Boolean = false,
    /** A nota esperando o "Apagar" da pessoa (o confirm do site). */
    val apagando: Nota? = null,
    @param:StringRes val aviso: Int? = null,
    val erro: MensagemDeErro? = null,
) {
    private val pronto: DadosDoCalendario get() = (dados as? Carga.Pronto)?.valor ?: DadosDoCalendario()

    val grade: List<DiaDaGrade?> by lazy { gradeDoCalendario(mes, pronto.gastos, pronto.receitas, pronto.notas, hoje) }
    val serie: List<DiaDaSerie> by lazy { serieDoMes(mes, pronto.gastos, pronto.receitas, hoje) }
    val semanas: List<SemanaDoMes> by lazy { semanasDoMes(mes, serie) }
    val diasNoMes: Int get() = YearMonth.parse(mes).lengthOfMonth()

    /** Nada entrou nem saiu no mês até agora: o gráfico avisa em vez de desenhar. */
    val semMovimento: Boolean get() = serie.lastOrNull().let { it == null || (it.receitasAteAqui.signum() == 0 && it.gastosAteAqui.signum() == 0) }

    /** Dia destacado no gráfico: o do dedo ou, sem ele, o aberto na grade. */
    val destaque: DiaDaSerie?
        get() {
            val dia = diaNoGrafico ?: diaEscolhido?.takeIf { it.startsWith("$mes-") }?.substring(8, 10)?.toInt()
            return dia?.let { serie.getOrNull(it - 1) }
        }

    val gastosDoDia: List<Gasto> get() = pronto.gastos.filter { it.data == diaEscolhido }
    val receitasDoDia: List<Receita> get() = pronto.receitas.filter { it.data == diaEscolhido }
    val lembretesDoDia: List<Nota> get() = pronto.notas.filter { diaEscolhido != null && it.data == diaEscolhido }
    val semData: List<Nota> get() = pronto.notas.filter { it.data.isNullOrEmpty() }
    val categorias: List<String> get() = pronto.categorias
}

/**
 * O Calendário (features/calendar e CalendarioView do iOS): a grade do mês com os gastos, as
 * receitas e os lembretes de cada dia, o gráfico acumulado e as notas. Recarrega quando muda o
 * mês, quando algo é lançado ([Lancamentos]) e quando a internet volta. Criar ou apagar nota
 * avisa as outras abas: o Painel mostra as notas, e a nota com valor mexe nos gastos.
 */
@HiltViewModel
class CalendarioViewModel internal constructor(
    private val financas: ServicoDeFinancas,
    private val cache: CacheDeRespostas,
    sessao: Sessao,
    private val lancamentos: Lancamentos,
    private val relogio: Clock,
    conectado: StateFlow<Boolean>,
) : ViewModel() {
    @Inject constructor(
        financas: ServicoDeFinancas,
        cache: CacheDeRespostas,
        sessao: Sessao,
        lancamentos: Lancamentos,
        relogio: Clock,
        rede: MonitorDeRede,
    ) : this(financas, cache, sessao, lancamentos, relogio, rede.conectado)

    private val mes = MutableStateFlow(relogio.mesAtual())
    private val recarga = MutableStateFlow(0)
    private val estadoAtual = MutableStateFlow(
        EstadoDoCalendario(
            mes = mes.value,
            hoje = LocalDate.now(relogio).toString(),
            moeda = sessao.usuario?.preferencias?.moeda ?: "EUR",
        ),
    )
    val estado: StateFlow<EstadoDoCalendario> = estadoAtual.asStateFlow()

    init {
        viewModelScope.launch {
            combine(mes, lancamentos.versao, recarga, conectado) { mes, _, _, conectado -> mes to conectado }
                .collectLatest { (mes, conectado) ->
                    estadoAtual.update { it.copy(conectado = conectado) }
                    carregar(mes)
                }
        }
    }

    fun mudarMes(novo: String) {
        mes.value = novo
        estadoAtual.update { it.copy(mes = novo, diaEscolhido = null, diaNoGrafico = null) }
    }

    fun tentarDeNovo() = recarga.update { it + 1 }

    /** Tocar no dia aberto fecha; abrir um dia já deixa a nova nota nele (como o site). */
    fun escolherDia(iso: String) = estadoAtual.update {
        val fechar = it.diaEscolhido == iso
        it.copy(diaEscolhido = if (fechar) null else iso, diaNoGrafico = null, nova = if (fechar) it.nova else it.nova.copy(data = iso, comData = true))
    }

    /** O dia no gráfico (dedo ou TalkBack), preso aos que já aconteceram; nulo apaga a dica. */
    fun apontarNoGrafico(dia: Int?) = estadoAtual.update { e ->
        e.copy(diaNoGrafico = dia?.takeIf { e.serie.isNotEmpty() }?.coerceIn(1, e.serie.size))
    }

    fun mudarTitulo(titulo: String) = estadoAtual.update { it.copy(nova = it.nova.copy(titulo = titulo.take(150)), faltouTitulo = false) }
    fun mudarTexto(texto: String) = editar { it.copy(texto = texto.take(1500)) }
    fun mudarComData(comData: Boolean) = editar { it.copy(comData = comData) }
    fun mudarData(data: String) = editar { it.copy(data = data) }
    fun mudarValor(valor: BigDecimal?) = editar { it.copy(valor = valor) }
    fun mudarCategoria(categoria: String) = editar { it.copy(categoria = categoria) }

    private fun editar(mudanca: (NotaEmEdicao) -> NotaEmEdicao) = estadoAtual.update { it.copy(nova = mudanca(it.nova)) }

    suspend fun criarCategoria(nome: String): Result<List<String>> =
        try {
            Result.success(financas.criarCategoria(PedidoDeNome(nome)).categorias)
        } catch (falha: ErroDaApi) {
            Result.failure(falha)
        }

    fun criarNota() {
        val e = estadoAtual.value
        if (e.guardando) return
        val nota = e.nova
        val titulo = nota.titulo.trim()
        when {
            titulo.isEmpty() -> estadoAtual.update { it.copy(faltouTitulo = true, erro = MensagemDeErro.DoApp(R.string.calendario_falta_titulo), aviso = null) }
            nota.viraGasto && !nota.comData -> estadoAtual.update { it.copy(erro = MensagemDeErro.DoApp(R.string.calendario_falta_data), aviso = null) }
            else -> {
                estadoAtual.update { it.copy(guardando = true, faltouTitulo = false, erro = null) }
                viewModelScope.launch {
                    try {
                        financas.criarNota(
                            NovaNota(
                                titulo = titulo,
                                texto = nota.texto.trim(),
                                data = nota.data.takeIf { nota.comData },
                                valor = nota.valor ?: BigDecimal.ZERO,
                                categoria = nota.categoria.takeIf { nota.viraGasto && it.isNotEmpty() },
                            ),
                        )
                        estadoAtual.update {
                            it.copy(guardando = false, nova = NotaEmEdicao(data = nota.data, categoria = nota.categoria), aviso = R.string.calendario_nota_criada)
                        }
                        lancamentos.avisar()
                    } catch (falha: ErroDaApi) {
                        estadoAtual.update { it.copy(guardando = false, erro = mensagemDeErro(falha), aviso = null) }
                    }
                }
            }
        }
    }

    fun pedirParaApagar(nota: Nota) = estadoAtual.update { it.copy(apagando = nota) }
    fun desistir() = estadoAtual.update { it.copy(apagando = null) }

    fun confirmar() {
        val nota = estadoAtual.value.apagando ?: return
        estadoAtual.update { it.copy(apagando = null) }
        viewModelScope.launch {
            try {
                financas.excluirNota(nota.id)
                estadoAtual.update { it.copy(erro = null, aviso = R.string.calendario_nota_apagada) }
                lancamentos.avisar()
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(falha), aviso = null) }
            }
        }
    }

    private suspend fun carregar(mes: String) {
        cache.carregar("calendario/$mes", DadosDoCalendario.serializer()) {
            coroutineScope {
                val notas = async { financas.listarNotas() }
                val gastos = async { financas.listarGastos(mes) }
                val receitas = async { financas.listarReceitas(mes) }
                val categorias = async { financas.listarCategorias().categorias }
                DadosDoCalendario(notas.await(), gastos.await(), receitas.await(), categorias.await())
            }
        }.collect { carga ->
            val guardadaEm = (carga as? Carga.Pronto)?.guardadaEm
            estadoAtual.update { e ->
                val categorias = (carga as? Carga.Pronto)?.valor?.categorias.orEmpty()
                e.copy(
                    dados = carga,
                    guardadoEm = guardadaEm?.let { quandoGuardado(it) },
                    diaNoGrafico = null,
                    nova = if (e.nova.categoria.isEmpty()) e.nova.copy(categoria = categorias.firstOrNull().orEmpty()) else e.nova,
                )
            }
        }
    }

    /** "28 de out., 10:00", como o AvisoDeGuardado do iOS. */
    private fun quandoGuardado(milissegundos: Long): String =
        DateTimeFormatter.ofPattern("d 'de' MMM, HH:mm", Locale.forLanguageTag("pt-BR"))
            .format(Instant.ofEpochMilli(milissegundos).atZone(relogio.zone))
}
