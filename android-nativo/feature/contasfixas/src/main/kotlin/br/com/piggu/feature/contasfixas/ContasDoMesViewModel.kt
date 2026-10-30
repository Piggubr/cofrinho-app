package br.com.piggu.feature.contasfixas

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.Lancamentos
import br.com.piggu.core.data.ListasParaLancar
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.mesAtual
import br.com.piggu.core.model.ContaFixa
import br.com.piggu.core.model.NovaContaFixa
import br.com.piggu.core.model.PedidoDeNome
import br.com.piggu.core.model.SituacaoDaContaFixa
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.core.network.mensagemDeErro
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** O que espera o "Apagar" ou o "Parar de repetir" da pessoa (o confirm do site). */
sealed interface Confirmacao {
    data class ApagarConta(val conta: ContaFixa) : Confirmacao
    data class PararDeRepetir(val fixa: ContaFixa) : Confirmacao
}

/** Nova conta fixa (o cartão "Nova conta fixa" do site), numa folha. */
data class FormularioDeContaFixa(
    val aberto: Boolean = false,
    val descricao: String = "",
    /** Vazia até as categorias chegarem; aí vem a primeira, como no site. */
    val categoria: String = "",
    val valor: BigDecimal? = null,
    /** Como a pessoa digitou: o dia só é conferido ao criar. */
    val dia: String = "10",
    /** Lança o gasto sozinha no dia do vencimento. */
    val automatico: Boolean = false,
    val erro: MensagemDeErro? = null,
)

data class EstadoDasContasDoMes(
    /** aaaa-mm da lista. */
    val mes: String,
    val mesAtual: String,
    val contas: Carga<List<ContaFixa>> = Carga.Carregando,
    /** Quando foi guardado o que aparece, se a atualização falhou ("28 de out., 10:00"). */
    val guardadoEm: String? = null,
    val conectado: Boolean = true,
    /** As receitas que entram sozinhas todo mês (as mesmas de Receitas). */
    val receitasFixas: List<ContaFixa> = emptyList(),
    val categorias: List<String> = emptyList(),
    val moeda: String = "EUR",
    /** Uma ação em andamento: os botões esperam (o "ocupado" do site). */
    val ocupado: Boolean = false,
    @param:StringRes val aviso: Int? = null,
    val erro: MensagemDeErro? = null,
    val confirmacao: Confirmacao? = null,
    val formulario: FormularioDeContaFixa = FormularioDeContaFixa(),
) {
    private val lista: List<ContaFixa> get() = (contas as? Carga.Pronto)?.valor.orEmpty()

    val vencidas: List<ContaFixa> get() = lista.filter { it.situacao == SituacaoDaContaFixa.VENCIDA }
    val pagas: List<ContaFixa> get() = lista.filter { it.situacao == SituacaoDaContaFixa.PAGA }

    /** Pendente e qualquer situação nova da API: não está paga nem vencida. */
    val aVencer: List<ContaFixa> get() = lista - vencidas.toSet() - pagas.toSet()

    val totalEmAberto: BigDecimal get() = (vencidas + aVencer).fold(BigDecimal.ZERO) { soma, conta -> soma + conta.valor }
}

/**
 * Contas do mês, as contas fixas (features/bills e ContasDoMesView do iOS): o que se repete todo
 * mês. "Marcar como paga" lança o gasto do mês; com o automático, o Piggu lança sozinho no dia.
 * Recarrega quando muda o mês, quando algo é lançado ([Lancamentos]) e quando a internet volta.
 */
@HiltViewModel
class ContasDoMesViewModel internal constructor(
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

    private val mes = MutableStateFlow(relogio.mesAtual())
    private val recarga = MutableStateFlow(0)
    private val estadoAtual = MutableStateFlow(
        EstadoDasContasDoMes(mes = mes.value, mesAtual = mes.value, moeda = sessao.usuario?.preferencias?.moeda ?: "EUR"),
    )
    val estado: StateFlow<EstadoDasContasDoMes> = estadoAtual.asStateFlow()

    init {
        viewModelScope.launch {
            combine(mes, lancamentos.versao, recarga, conectado) { mes, _, _, conectado -> mes to conectado }
                .collectLatest { (mes, conectado) ->
                    estadoAtual.update { it.copy(conectado = conectado) }
                    carregar(mes)
                }
        }
        viewModelScope.launch {
            listas.categorias().collect { carga ->
                if (carga is Carga.Pronto) categoriasCriadas(carga.valor.categorias)
            }
        }
    }

    fun mudarMes(novo: String) {
        mes.value = novo
        estadoAtual.update { it.copy(mes = novo) }
    }

    fun tentarDeNovo() = recarga.update { it + 1 }

    /** Lança o gasto da conta no mês da tela. */
    fun pagar(conta: ContaFixa) {
        val mes = estadoAtual.value.mes
        executar(R.string.contas_paga_aviso) { financas.pagarContaFixa(conta.id, mes) }
    }

    fun pedirParaApagar(conta: ContaFixa) = estadoAtual.update { it.copy(confirmacao = Confirmacao.ApagarConta(conta)) }
    fun pedirParaPararDeRepetir(fixa: ContaFixa) = estadoAtual.update { it.copy(confirmacao = Confirmacao.PararDeRepetir(fixa)) }
    fun desistir() = estadoAtual.update { it.copy(confirmacao = null) }

    fun confirmar() {
        val confirmacao = estadoAtual.value.confirmacao ?: return
        estadoAtual.update { it.copy(confirmacao = null) }
        when (confirmacao) {
            is Confirmacao.ApagarConta -> executar(R.string.contas_apagada_aviso) { financas.excluirContaFixa(confirmacao.conta.id) }
            is Confirmacao.PararDeRepetir -> executar(R.string.contas_parou_aviso) { financas.excluirContaFixa(confirmacao.fixa.id) }
        }
    }

    fun abrirFormulario() = mudarFormulario { it.copy(aberto = true, erro = null) }
    fun fecharFormulario() = mudarFormulario { it.copy(aberto = false, erro = null) }
    fun mudarDescricao(descricao: String) = mudarFormulario { it.copy(descricao = descricao.take(200)) }
    fun mudarCategoria(categoria: String) = mudarFormulario { it.copy(categoria = categoria) }
    fun mudarValor(valor: BigDecimal?) = mudarFormulario { it.copy(valor = valor) }
    fun mudarDia(dia: String) = mudarFormulario { it.copy(dia = dia.filter(Char::isDigit).take(2)) }
    fun mudarAutomatico(automatico: Boolean) = mudarFormulario { it.copy(automatico = automatico) }

    /** Recebe as categorias (a carga ou depois de criar uma); sem nenhuma escolhida, vem a primeira. */
    fun categoriasCriadas(lista: List<String>) = estadoAtual.update {
        it.copy(categorias = lista, formulario = it.formulario.copy(categoria = it.formulario.categoria.ifEmpty { lista.firstOrNull().orEmpty() }))
    }

    /** Cria a categoria no servidor; a falha vem com o [ErroDaApi] para a tela escrever. */
    suspend fun criarCategoria(nome: String): Result<List<String>> =
        try {
            Result.success(financas.criarCategoria(PedidoDeNome(nome)).categorias)
        } catch (erro: ErroDaApi) {
            Result.failure(erro)
        }

    fun criar() {
        val e = estadoAtual.value
        if (e.ocupado) return
        val f = e.formulario
        val descricao = f.descricao.trim()
        val valor = f.valor
        val dia = f.dia.toIntOrNull()
        if (descricao.isEmpty() || valor == null || valor.signum() <= 0 || dia == null || dia !in 1..31) {
            mudarFormulario { it.copy(erro = MensagemDeErro.DoApp(R.string.contas_nova_invalida)) }
            return
        }
        estadoAtual.update { it.copy(ocupado = true, formulario = it.formulario.copy(erro = null)) }
        viewModelScope.launch {
            try {
                financas.criarContaFixa(NovaContaFixa(descricao, f.categoria, valor, dia, f.automatico))
                // Como o site: categoria, dia e automático ficam para a próxima.
                estadoAtual.update {
                    it.copy(
                        ocupado = false, aviso = R.string.contas_criada_aviso, erro = null,
                        formulario = it.formulario.copy(aberto = false, descricao = "", valor = null),
                    )
                }
                lancamentos.avisar()
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(ocupado = false, formulario = it.formulario.copy(erro = mensagemDeErro(falha))) }
            }
        }
    }

    private fun mudarFormulario(mudar: (FormularioDeContaFixa) -> FormularioDeContaFixa) =
        estadoAtual.update { it.copy(formulario = mudar(it.formulario)) }

    /** Deu certo: mostra o [aviso] e avisa as outras telas (esta também ouve e recarrega). */
    private fun executar(@StringRes aviso: Int, acao: suspend () -> Unit) {
        if (estadoAtual.value.ocupado) return
        estadoAtual.update { it.copy(ocupado = true, erro = null, aviso = null) }
        viewModelScope.launch {
            try {
                acao()
                estadoAtual.update { it.copy(ocupado = false, aviso = aviso) }
                lancamentos.avisar()
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(ocupado = false, erro = mensagemDeErro(falha)) }
            }
        }
    }

    /** A lista manda nos estados; as receitas fixas, sem rede, ficam com o guardado ou somem, como no iOS. */
    private suspend fun carregar(mes: String) = coroutineScope {
        launch {
            cache.carregar("contas-do-mes/$mes", ListSerializer(ContaFixa.serializer())) { financas.contasFixas(mes) }.collect { carga ->
                val guardadaEm = (carga as? Carga.Pronto)?.guardadaEm
                estadoAtual.update { it.copy(contas = carga, guardadoEm = guardadaEm?.let(::quandoGuardado)) }
            }
        }
        launch {
            cache.carregar("receitas-recorrentes/$mes", ListSerializer(ContaFixa.serializer())) { financas.receitasRecorrentes(mes) }
                .collect { carga ->
                    when (carga) {
                        is Carga.Pronto -> estadoAtual.update { it.copy(receitasFixas = carga.valor) }
                        is Carga.Falhou -> estadoAtual.update { it.copy(receitasFixas = emptyList()) }
                        Carga.Carregando -> Unit
                    }
                }
        }
    }

    /** "28 de out., 10:00", como o AvisoDeGuardado do iOS. */
    private fun quandoGuardado(milissegundos: Long): String =
        DateTimeFormatter.ofPattern("d 'de' MMM, HH:mm", Locale.forLanguageTag("pt-BR"))
            .format(Instant.ofEpochMilli(milissegundos).atZone(relogio.zone))
}
