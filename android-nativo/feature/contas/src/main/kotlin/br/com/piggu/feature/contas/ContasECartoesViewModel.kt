package br.com.piggu.feature.contas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.Lancamentos
import br.com.piggu.core.data.ListasParaLancar
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.model.ContaBancaria
import br.com.piggu.core.model.ContaOuCartao
import br.com.piggu.core.model.FaturaComGastos
import br.com.piggu.core.model.MembroDaFamilia
import br.com.piggu.core.model.NovaConta
import br.com.piggu.core.model.PedidoDeCompartilhar
import br.com.piggu.core.model.TipoDeConta
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeBancos
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.formatos.fechamentoPadrao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

/** O campo da nova conta que barrou o envio, para marcar como inválido. */
enum class CampoDaConta { Nome, Vencimento, Fechamento }

/** O que espera o "Apagar" ou o "Desconectar" da pessoa (o confirm do site). */
sealed interface Confirmacao {
    data class ApagarConta(val conta: ContaOuCartao) : Confirmacao
    data class Desconectar(val conexaoId: String, val instituicao: String) : Confirmacao
}

/** A fatura aberta: um cartão por vez, andando de mês em mês. */
data class FaturaNaTela(val cartao: ContaOuCartao, val mes: String, val carga: Carga<FaturaComGastos> = Carga.Carregando)

data class EstadoDasContasECartoes(
    val contas: Carga<List<ContaOuCartao>> = Carga.Carregando,
    /** Quando foi guardado o que aparece, se a atualização falhou ("28 de out., 10:00"). */
    val guardadoEm: String? = null,
    val conectado: Boolean = true,
    val membros: List<MembroDaFamilia> = emptyList(),
    val eu: String? = null,
    val moeda: String = "EUR",
    val erro: MensagemDeErro? = null,
    val confirmacao: Confirmacao? = null,
    // Nova conta ou cartão.
    val nome: String = "",
    val tipo: TipoDeConta = TipoDeConta.CARTAO,
    /** Os dias como a pessoa digitou: só são conferidos ao salvar. */
    val vencimento: String = "",
    val fechamento: String = "",
    val compartilhada: Boolean = false,
    val faltou: CampoDaConta? = null,
    val salvando: Boolean = false,
    val fatura: FaturaNaTela? = null,
    val pagando: Boolean = false,
    // Bancos do Open Finance.
    /** GET /api/banking/status: só com ele ligado aparece "Conectar banco". */
    val openFinanceLigado: Boolean = false,
    val bancos: List<ContaBancaria> = emptyList(),
    val mexendoNosBancos: Boolean = false,
) {
    /** Mora mais alguém na casa: só assim aparecem o "do casal" e de quem é a conta. */
    val temCompanhia: Boolean get() = membros.size > 1

    /** Quem criou (ou conta antiga, sem dono) compartilha e apaga. */
    fun minha(conta: ContaOuCartao): Boolean = conta.usuario == null || conta.usuario == eu

    fun podeCompartilhar(conta: ContaOuCartao): Boolean = minha(conta) && temCompanhia

    /** "De Beto" na conta de outra pessoa da casa. */
    fun deQuem(conta: ContaOuCartao): Aviso? = when {
        minha(conta) || !temCompanhia -> null
        else -> membros.firstOrNull { it.id == conta.usuario }?.let { Aviso(R.string.contas_de, listOf(it.nome)) }
            ?: Aviso(R.string.contas_de_ex_membro)
    }

    /** Desligado o Open Finance, quem já tem banco conectado ainda atualiza e desconecta. */
    val mostrarBancos: Boolean get() = openFinanceLigado || bancos.isNotEmpty()

    /** Um banco por conexão, para o "Desconectar" não repetir por conta. */
    val conexoes: List<Pair<String, String>> get() = bancos.associate { it.conexaoId to it.instituicao }.toList()
}

/**
 * Cartões e bancos (features/accounts e ContasECartoesView do iOS): as contas e cartões de
 * lançar, a fatura do cartão com "Marcar como paga" (D-085) e os bancos do Open Finance.
 * Recarrega quando algo é lançado ([Lancamentos]) e quando a internet volta; toda mudança avisa
 * as outras telas (o Painel mostra a fatura a pagar).
 */
@HiltViewModel
class ContasECartoesViewModel internal constructor(
    private val financas: ServicoDeFinancas,
    private val bancos: ServicoDeBancos,
    private val cache: CacheDeRespostas,
    private val listas: ListasParaLancar,
    sessao: Sessao,
    private val lancamentos: Lancamentos,
    private val relogio: Clock,
    conectado: StateFlow<Boolean>,
) : ViewModel() {
    @Inject constructor(
        financas: ServicoDeFinancas,
        bancos: ServicoDeBancos,
        cache: CacheDeRespostas,
        listas: ListasParaLancar,
        sessao: Sessao,
        lancamentos: Lancamentos,
        relogio: Clock,
        rede: MonitorDeRede,
    ) : this(financas, bancos, cache, listas, sessao, lancamentos, relogio, rede.conectado)

    private val recarga = MutableStateFlow(0)
    private val estadoAtual = MutableStateFlow(
        EstadoDasContasECartoes(eu = sessao.usuario?.id, moeda = sessao.usuario?.preferencias?.moeda ?: "EUR"),
    )
    val estado: StateFlow<EstadoDasContasECartoes> = estadoAtual.asStateFlow()
    private var buscaDaFatura: Job? = null

    init {
        viewModelScope.launch {
            combine(lancamentos.versao, recarga, conectado) { _, _, conectado -> conectado }.collectLatest { conectado ->
                estadoAtual.update { it.copy(conectado = conectado) }
                carregar()
            }
        }
    }

    fun tentarDeNovo() = recarga.update { it + 1 }

    // Nova conta ou cartão

    fun mudarNome(nome: String) = estadoAtual.update { it.copy(nome = nome.take(60)) }
    fun mudarTipo(tipo: TipoDeConta) = estadoAtual.update { it.copy(tipo = tipo) }
    fun mudarFechamento(dia: String) = estadoAtual.update { it.copy(fechamento = soODia(dia)) }
    fun mudarCompartilhada(compartilhada: Boolean) = estadoAtual.update { it.copy(compartilhada = compartilhada) }

    /** O fechamento acompanha o vencimento (7 dias antes) até a pessoa mudar ele à mão. */
    fun mudarVencimento(dia: String) = estadoAtual.update {
        val novo = soODia(dia)
        val acompanhava = it.fechamento.isEmpty() || it.vencimento.toIntOrNull()?.let { v -> fechamentoPadrao(v).toString() } == it.fechamento
        val valido = novo.toIntOrNull()?.takeIf { d -> d in 1..31 }
        it.copy(vencimento = novo, fechamento = if (acompanhava && valido != null) fechamentoPadrao(valido).toString() else it.fechamento)
    }

    fun salvar() {
        val e = estadoAtual.value
        if (e.salvando) return
        val nome = e.nome.trim()
        val cartao = e.tipo == TipoDeConta.CARTAO
        val vencimento = e.vencimento.toIntOrNull()?.takeIf { it in 1..31 }
        val fechamento = e.fechamento.toIntOrNull()?.takeIf { it in 1..31 }
        // Na ordem do site: nome, vencimento, fechamento.
        when {
            nome.length < 2 -> recusar(CampoDaConta.Nome, R.string.nova_conta_falta_nome)
            cartao && vencimento == null -> recusar(CampoDaConta.Vencimento, R.string.nova_conta_falta_vencimento)
            cartao && fechamento == null -> recusar(CampoDaConta.Fechamento, R.string.nova_conta_falta_fechamento)
            else -> {
                estadoAtual.update { it.copy(salvando = true, faltou = null, erro = null) }
                viewModelScope.launch {
                    try {
                        financas.criarConta(
                            NovaConta(
                                nome = nome,
                                tipo = e.tipo,
                                fechamento = if (cartao) fechamento else null,
                                vencimento = if (cartao) vencimento else null,
                                compartilhado = e.temCompanhia && e.compartilhada,
                            ),
                        )
                        // Limpa como o site: o tipo fica para a próxima.
                        estadoAtual.update { it.copy(nome = "", vencimento = "", fechamento = "", compartilhada = false, salvando = false) }
                        lancamentos.avisar()
                    } catch (falha: ErroDaApi) {
                        estadoAtual.update { it.copy(salvando = false, erro = mensagemDeErro(falha)) }
                    }
                }
            }
        }
    }

    private fun recusar(campo: CampoDaConta, texto: Int) = estadoAtual.update { it.copy(faltou = campo, erro = MensagemDeErro.DoApp(texto)) }

    // Compartilhar e apagar

    /** Só quem criou liga ou desliga; os gastos já lançados não mudam. */
    fun alternarCompartilhamento(conta: ContaOuCartao) = executar {
        financas.compartilharConta(conta.id, PedidoDeCompartilhar(conta.compartilhado != true))
    }

    fun pedirParaApagar(conta: ContaOuCartao) = estadoAtual.update { it.copy(confirmacao = Confirmacao.ApagarConta(conta)) }
    fun pedirParaDesconectar(conexaoId: String, instituicao: String) =
        estadoAtual.update { it.copy(confirmacao = Confirmacao.Desconectar(conexaoId, instituicao)) }
    fun desistir() = estadoAtual.update { it.copy(confirmacao = null) }

    fun confirmar() {
        val confirmacao = estadoAtual.value.confirmacao ?: return
        estadoAtual.update { it.copy(confirmacao = null) }
        when (confirmacao) {
            is Confirmacao.ApagarConta -> executar {
                financas.excluirConta(confirmacao.conta.id)
                if (estadoAtual.value.fatura?.cartao?.id == confirmacao.conta.id) fecharFatura()
            }
            is Confirmacao.Desconectar -> agirNosBancos { bancos.desconectar(confirmacao.conexaoId) }
        }
    }

    // Fatura do cartão

    fun verFatura(cartao: ContaOuCartao) {
        abrirFatura(cartao, cartao.faturaAberta?.mes ?: return)
    }

    fun faturaAnterior() = andarNaFatura(-1)
    fun proximaFatura() = andarNaFatura(1)

    fun fecharFatura() {
        buscaDaFatura?.cancel()
        estadoAtual.update { it.copy(fatura = null) }
    }

    /** Só tira a fatura do "a pagar" (com as anteriores); os gastos já contaram nas compras (D-085). */
    fun alternarPagamento() {
        val e = estadoAtual.value
        val aberta = e.fatura ?: return
        val fatura = (aberta.carga as? Carga.Pronto)?.valor?.fatura ?: return
        if (e.pagando) return
        estadoAtual.update { it.copy(pagando = true, erro = null) }
        viewModelScope.launch {
            try {
                if (fatura.paga == true) {
                    financas.desfazerPagamentoDaFatura(aberta.cartao.id, fatura.mes)
                } else {
                    financas.pagarFatura(aberta.cartao.id, fatura.mes)
                }
                lancamentos.avisar() // recarrega a lista e a fatura aberta
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(falha)) }
            } finally {
                estadoAtual.update { it.copy(pagando = false) }
            }
        }
    }

    private fun andarNaFatura(passo: Long) {
        val aberta = estadoAtual.value.fatura ?: return
        abrirFatura(aberta.cartao, YearMonth.parse(aberta.mes).plusMonths(passo).toString())
    }

    private fun abrirFatura(cartao: ContaOuCartao, mes: String) {
        estadoAtual.update { it.copy(fatura = FaturaNaTela(cartao, mes), erro = null) }
        buscaDaFatura?.cancel()
        buscaDaFatura = viewModelScope.launch { buscarFatura(cartao, mes) }
    }

    /** Só vale se a pessoa ainda está na mesma fatura: andar rápido não mostra a de outro mês. */
    private suspend fun buscarFatura(cartao: ContaOuCartao, mes: String) {
        val carga = try {
            Carga.Pronto(financas.fatura(cartao.id, mes))
        } catch (falha: ErroDaApi) {
            Carga.Falhou(falha)
        }
        estadoAtual.update { e ->
            val aberta = e.fatura
            if (aberta?.cartao?.id == cartao.id && aberta.mes == mes) e.copy(fatura = aberta.copy(carga = carga)) else e
        }
    }

    // Bancos

    fun atualizarBancos() = agirNosBancos { bancos.sincronizar() }

    private fun agirNosBancos(chamada: suspend () -> List<ContaBancaria>) {
        if (estadoAtual.value.mexendoNosBancos) return
        estadoAtual.update { it.copy(mexendoNosBancos = true, erro = null) }
        viewModelScope.launch {
            try {
                val contas = chamada()
                estadoAtual.update { it.copy(bancos = contas) }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(falha)) }
            } finally {
                estadoAtual.update { it.copy(mexendoNosBancos = false) }
            }
        }
    }

    /** Deu certo: tira o erro e avisa as outras telas (que esta também ouve, e recarrega). */
    private fun executar(acao: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                acao()
                estadoAtual.update { it.copy(erro = null) }
                lancamentos.avisar()
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(falha)) }
            }
        }
    }

    /**
     * A lista de contas manda nos estados. O Open Finance é opcional: o status fora do ar conta
     * como desligado, e os bancos ficam com o guardado ou somem, sem erro na tela.
     */
    private suspend fun carregar() = coroutineScope {
        launch {
            listas.contas().collect { carga ->
                val guardadaEm = (carga as? Carga.Pronto)?.guardadaEm
                estadoAtual.update { it.copy(contas = carga, guardadoEm = guardadaEm?.let { quando -> quandoGuardado(quando, relogio.zone) }) }
            }
        }
        launch {
            listas.familia().collect { carga ->
                if (carga is Carga.Pronto) estadoAtual.update { it.copy(membros = carga.valor.membros) }
            }
        }
        launch {
            val ligado = try {
                bancos.status().habilitado
            } catch (_: ErroDaApi) {
                false
            }
            estadoAtual.update { it.copy(openFinanceLigado = ligado) }
        }
        launch {
            cache.carregar("bancos", ListSerializer(ContaBancaria.serializer())) { bancos.listarContas() }.collect { carga ->
                if (carga is Carga.Pronto) estadoAtual.update { it.copy(bancos = carga.valor) }
            }
        }
        estadoAtual.value.fatura?.let { launch { buscarFatura(it.cartao, it.mes) } }
    }

    private fun soODia(texto: String) = texto.filter(Char::isDigit).take(2)
}
