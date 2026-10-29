package br.com.piggu.feature.orcamentos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.EstadoDaSessao
import br.com.piggu.core.data.Lancamentos
import br.com.piggu.core.data.ListasParaLancar
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.ehPremium
import br.com.piggu.core.data.mesAtual
import br.com.piggu.core.model.Decimal
import br.com.piggu.core.model.Orcamento
import br.com.piggu.core.model.PedidoDeMeta
import br.com.piggu.core.model.PedidoDeNome
import br.com.piggu.core.model.PedidoDeOrcamento
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
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** O que a tela busca de uma vez e guarda para abrir sem internet ("orcamento/2026-10"). */
@Serializable
data class DadosDoOrcamento(
    /** AAAA-MM para o limite total daquele mês (monthly-goals). */
    val metas: Map<String, Decimal> = emptyMap(),
    /** A soma dos gastos do mês atual, como o goals.ts. */
    val gastoDoMes: Decimal = BigDecimal.ZERO,
    /** Os limites por categoria do mês atual, com o alerta que o backend calcula. */
    val orcamentos: List<Orcamento> = emptyList(),
)

data class EstadoDoOrcamento(
    /** aaaa-mm. */
    val mesAtual: String,
    val moeda: String = "EUR",
    val premium: Boolean = false,
    val dados: Carga<DadosDoOrcamento> = Carga.Carregando,
    /** Quando foi guardado o que aparece, se a atualização falhou ("28 de out., 10:00"). */
    val guardadoEm: String? = null,
    val conectado: Boolean = true,
    val categorias: List<String> = emptyList(),
    /** O formulário "Definir o limite". */
    val mesDoLimite: String = mesAtual,
    val limite: BigDecimal? = null,
    /** O formulário do limite por categoria (só Premium). */
    val categoria: String = "",
    val limiteDaCategoria: BigDecimal? = null,
    val salvando: Boolean = false,
    val erro: MensagemDeErro? = null,
) {
    private val pronto: DadosDoOrcamento get() = (dados as? Carga.Pronto)?.valor ?: DadosDoOrcamento()

    val metaDoMesAtual: BigDecimal get() = pronto.metas[mesAtual] ?: BigDecimal.ZERO
    val gastoDoMes: BigDecimal get() = pronto.gastoDoMes
    val orcamentos: List<Orcamento> get() = pronto.orcamentos

    /** Quanto do limite do mês já foi usado, de 0 a 100 (a barra para em 100, como no site). */
    val progresso: Int
        get() = if (metaDoMesAtual.signum() <= 0) {
            0
        } else {
            gastoDoMes.multiply(CEM).divide(metaDoMesAtual, 0, RoundingMode.HALF_UP).toInt().coerceIn(0, 100)
        }

    val estourou: Boolean get() = metaDoMesAtual.signum() > 0 && gastoDoMes > metaDoMesAtual

    /** Todos os limites, do mês mais novo para o mais velho ("Limites dos outros meses" do site). */
    val outrosMeses: List<Pair<String, BigDecimal>> get() = pronto.metas.entries.sortedByDescending { it.key }.map { it.key to it.value }

    /** O seletor do formulário vai até um ano à frente: limite se define antes do mês chegar. */
    val ultimoMesDoLimite: String get() = YearMonth.parse(mesAtual).plusMonths(12).toString()
}

private val CEM = BigDecimal(100)

/**
 * O Orçamento (features/budgets, com o features/goals no topo, e OrcamentoView do iOS): o limite
 * total do mês (grátis) e o limite por categoria (Premium), com alerta em 80% e 100%. Ver e apagar
 * o que já existe é livre, como no site. Recarrega quando algo é lançado ([Lancamentos]) e
 * quando a internet volta; toda mudança avisa as outras telas (o Painel mostra o limite e os alertas).
 */
@HiltViewModel
class OrcamentoViewModel internal constructor(
    private val financas: ServicoDeFinancas,
    private val cache: CacheDeRespostas,
    private val listas: ListasParaLancar,
    sessao: Sessao,
    private val lancamentos: Lancamentos,
    private val relogio: Clock,
    conectado: StateFlow<Boolean>,
) : ViewModel() {
    @Inject constructor(
        financas: ServicoDeFinancas,
        cache: CacheDeRespostas,
        listas: ListasParaLancar,
        sessao: Sessao,
        lancamentos: Lancamentos,
        relogio: Clock,
        rede: MonitorDeRede,
    ) : this(financas, cache, listas, sessao, lancamentos, relogio, rede.conectado)

    private val recarga = MutableStateFlow(0)
    private val estadoAtual = MutableStateFlow(
        EstadoDoOrcamento(mesAtual = relogio.mesAtual(), moeda = sessao.usuario?.preferencias?.moeda ?: "EUR"),
    )
    val estado: StateFlow<EstadoDoOrcamento> = estadoAtual.asStateFlow()

    init {
        // O plano pode mudar com a tela aberta (assinou o Premium e voltou).
        viewModelScope.launch {
            sessao.estado.collect { s ->
                val usuario = (s as? EstadoDaSessao.Logado)?.usuario
                estadoAtual.update { it.copy(premium = usuario?.ehPremium == true, moeda = usuario?.preferencias?.moeda ?: it.moeda) }
            }
        }
        viewModelScope.launch {
            listas.categorias().collect { carga ->
                if (carga is Carga.Pronto) estadoAtual.update { it.copy(categorias = carga.valor.categorias) }
            }
        }
        viewModelScope.launch {
            combine(lancamentos.versao, recarga, conectado) { _, _, conectado -> conectado }
                .collectLatest { conectado ->
                    estadoAtual.update { it.copy(conectado = conectado) }
                    carregar()
                }
        }
    }

    fun tentarDeNovo() = recarga.update { it + 1 }

    fun mudarMesDoLimite(mes: String) = estadoAtual.update { it.copy(mesDoLimite = mes, limite = metas()[mes]) }
    fun mudarLimite(valor: BigDecimal?) = estadoAtual.update { it.copy(limite = valor) }

    /** O lápis de um mês da lista leva o mês e o valor ao formulário. */
    fun editarMeta(mes: String) = mudarMesDoLimite(mes)

    fun salvarLimite() {
        val e = estadoAtual.value
        if (e.salvando) return
        val limite = e.limite
        if (limite == null || limite.signum() <= 0) return recusar(R.string.orcamento_limite_invalido)
        executar {
            val metas = financas.definirMeta(PedidoDeMeta(e.mesDoLimite, limite))
            // A resposta já é a lista nova: aparece sem esperar a recarga.
            estadoAtual.update { atual ->
                val dados = atual.dados
                if (dados is Carga.Pronto) atual.copy(dados = Carga.Pronto(dados.valor.copy(metas = metas), dados.guardadaEm)) else atual
            }
        }
    }

    fun mudarCategoria(categoria: String) = estadoAtual.update { it.copy(categoria = categoria) }
    fun mudarLimiteDaCategoria(valor: BigDecimal?) = estadoAtual.update { it.copy(limiteDaCategoria = valor) }

    /** Mudar um limite que já existe é do Premium: o lápis só aparece para quem tem. */
    fun editarOrcamento(orcamento: Orcamento) =
        estadoAtual.update { it.copy(categoria = orcamento.categoria, limiteDaCategoria = orcamento.limite) }

    suspend fun criarCategoria(nome: String): Result<List<String>> =
        try {
            Result.success(financas.criarCategoria(PedidoDeNome(nome)).categorias)
        } catch (falha: ErroDaApi) {
            Result.failure(falha)
        }

    fun categoriasCriadas(categorias: List<String>) = estadoAtual.update { it.copy(categorias = categorias) }

    fun salvarCategoria() {
        val e = estadoAtual.value
        // Sem Premium a tela mostra o convite no lugar do formulário; o backend recusaria (402).
        if (e.salvando || !e.premium) return
        val limite = e.limiteDaCategoria
        if (e.categoria.isBlank() || limite == null || limite.signum() <= 0) return recusar(R.string.orcamento_categoria_invalida)
        executar {
            financas.definirOrcamento(PedidoDeOrcamento(e.categoria, limite))
            estadoAtual.update { it.copy(categoria = "", limiteDaCategoria = null) }
        }
    }

    /** Apagar é livre, com ou sem Premium (como no site). */
    fun excluir(orcamento: Orcamento) = executar { financas.excluirOrcamento(orcamento.id) }

    private fun metas(): Map<String, BigDecimal> = (estadoAtual.value.dados as? Carga.Pronto)?.valor?.metas.orEmpty()

    private fun recusar(texto: Int) = estadoAtual.update { it.copy(erro = MensagemDeErro.DoApp(texto)) }

    /** Deu certo: tira o erro e avisa as outras telas (que esta também ouve, e recarrega). */
    private fun executar(acao: suspend () -> Unit) {
        estadoAtual.update { it.copy(salvando = true, erro = null) }
        viewModelScope.launch {
            try {
                acao()
                estadoAtual.update { it.copy(salvando = false) }
                lancamentos.avisar()
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(salvando = false, erro = mensagemDeErro(falha)) }
            }
        }
    }

    private suspend fun carregar() {
        val mes = estadoAtual.value.mesAtual
        cache.carregar("orcamento/$mes", DadosDoOrcamento.serializer()) {
            coroutineScope {
                val metas = async { financas.listarMetas() }
                val gastos = async { financas.listarGastos(mes) }
                val orcamentos = async { financas.orcamentos(mes) }
                DadosDoOrcamento(metas.await(), gastos.await().fold(BigDecimal.ZERO) { soma, g -> soma + g.valor }, orcamentos.await())
            }
        }.collect { carga ->
            estadoAtual.update { atual ->
                val metas = (carga as? Carga.Pronto)?.valor?.metas
                atual.copy(
                    dados = carga,
                    guardadoEm = (carga as? Carga.Pronto)?.guardadaEm?.let { quandoGuardado(it, relogio.zone) },
                    // Como o site: ao abrir, o formulário já vem com o limite do mês escolhido.
                    limite = atual.limite ?: metas?.get(atual.mesDoLimite),
                )
            }
        }
    }
}

/** "28 de out., 10:00": quando o guardado foi gravado, como o AvisoDeGuardado do iOS. */
internal fun quandoGuardado(milissegundos: Long, zona: ZoneId): String =
    DateTimeFormatter.ofPattern("d 'de' MMM, HH:mm", Locale.forLanguageTag("pt-BR"))
        .format(Instant.ofEpochMilli(milissegundos).atZone(zona))
