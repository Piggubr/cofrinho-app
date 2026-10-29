package br.com.piggu.feature.premios

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.Lancamentos
import br.com.piggu.core.data.ListasParaLancar
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.cuidaDosPremios
import br.com.piggu.core.model.MembroDaFamilia
import br.com.piggu.core.model.NovoPremio
import br.com.piggu.core.model.PedidoDeAjuste
import br.com.piggu.core.model.Premio
import br.com.piggu.core.model.Resgate
import br.com.piggu.core.model.SaldoDeMoedas
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeRecompensas
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
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Autor do que o próprio Piggu faz (SISTEMA de pessoas-da-familia.ts). */
internal const val SISTEMA = "00000000-0000-0000-0000-000000000000"

/** O que a tela mostra, buscado junto e guardado numa chave só ("premios"), como no iOS. */
@Serializable
data class DadosDosPremios(val saldo: SaldoDeMoedas, val premios: List<Premio>, val resgates: List<Resgate>)

/** Aviso de sucesso para a tela: um texto do app com os argumentos. */
data class Aviso(@param:StringRes val texto: Int, val argumentos: List<Any> = emptyList())

/** O que espera o "sim" da pessoa (os confirm do site). */
sealed interface Confirmacao {
    data class Resgatar(val premio: Premio) : Confirmacao
    data class Remover(val premio: Premio) : Confirmacao
}

/** Novo prêmio (o cartão do site) ou editar um, numa folha. */
data class FormularioDePremio(
    val aberto: Boolean = false,
    /** Nulo ao criar. */
    val editando: Premio? = null,
    val nome: String = "",
    val descricao: String = "",
    /** Como a pessoa digitou, só dígitos: conferido ao salvar. */
    val preco: String = "",
    val erro: MensagemDeErro? = null,
)

data class EstadoDosPremios(
    /** Titular (ou ADMIN) e parceiro: ajustam o saldo e cuidam dos prêmios (rewards.ts). */
    val cuidaDosPremios: Boolean = false,
    val carga: Carga<DadosDosPremios> = Carga.Carregando,
    /** Quando foi guardado o que aparece, se a atualização falhou ("28 de out., 10:00"). */
    val guardadoEm: String? = null,
    val conectado: Boolean = true,
    val membros: List<MembroDaFamilia> = emptyList(),
    val mostrarHistorico: Boolean = false,
    /** Como a pessoa digitou: o sinal e os dígitos. */
    val ajusteValor: String = "",
    val ajusteMotivo: String = "",
    /** Uma ação em andamento: os botões esperam. */
    val ocupado: Boolean = false,
    val aviso: Aviso? = null,
    val erro: MensagemDeErro? = null,
    val confirmacao: Confirmacao? = null,
    val formulario: FormularioDePremio = FormularioDePremio(),
) {
    val dados: DadosDosPremios? get() = (carga as? Carga.Pronto)?.valor
    val saldo: Int get() = dados?.saldo?.saldo ?: 0

    /** Como o site: só o prêmio ativo que o saldo paga. */
    fun podeResgatar(premio: Premio): Boolean = premio.ativo && saldo >= premio.preco && !ocupado

    /** Quem resgatou (nomeNaFamilia de pessoas-da-familia.ts): o nome, o Piggu ou um Ex-membro. */
    fun quemResgatou(resgate: Resgate): Aviso = when (resgate.usuario) {
        SISTEMA -> Aviso(R.string.premios_piggu)
        else -> membros.firstOrNull { it.id == resgate.usuario }?.let { Aviso(R.string.premios_pessoa, listOf(it.nome)) }
            ?: Aviso(R.string.premios_ex_membro)
    }
}

/**
 * Prêmios (features/rewards e PremiosView do iOS): as Fofocoins, os prêmios e os resgates.
 * Ajustar o saldo e criar, editar e excluir prêmios é de quem cuida dos prêmios; resgatar é de
 * todos. O backend recusa de qualquer jeito, mas a tela não oferece o que sempre daria erro.
 * Recarrega depois de cada ação, quando algo é lançado ([Lancamentos]) e quando a internet volta.
 */
@HiltViewModel
class PremiosViewModel internal constructor(
    private val recompensas: ServicoDeRecompensas,
    private val cache: CacheDeRespostas,
    listas: ListasParaLancar,
    sessao: Sessao,
    lancamentos: Lancamentos,
    private val relogio: Clock,
    conectado: StateFlow<Boolean>,
) : ViewModel() {
    @Inject constructor(
        recompensas: ServicoDeRecompensas,
        cache: CacheDeRespostas,
        listas: ListasParaLancar,
        sessao: Sessao,
        lancamentos: Lancamentos,
        relogio: Clock,
        rede: MonitorDeRede,
    ) : this(recompensas, cache, listas, sessao, lancamentos, relogio, rede.conectado)

    private val recarga = MutableStateFlow(0)
    private val estadoAtual = MutableStateFlow(EstadoDosPremios(cuidaDosPremios = sessao.usuario?.cuidaDosPremios == true))
    val estado: StateFlow<EstadoDosPremios> = estadoAtual.asStateFlow()

    init {
        viewModelScope.launch {
            combine(lancamentos.versao, recarga, conectado) { _, _, conectado -> conectado }.collectLatest { conectado ->
                estadoAtual.update { it.copy(conectado = conectado) }
                val todos = estadoAtual.value.cuidaDosPremios
                cache.carregar("premios", DadosDosPremios.serializer()) { buscar(todos) }.collect { carga ->
                    val guardadaEm = (carga as? Carga.Pronto)?.guardadaEm
                    estadoAtual.update { it.copy(carga = carga, guardadoEm = guardadaEm?.let(::quandoGuardado)) }
                }
            }
        }
        viewModelScope.launch {
            listas.familia().collect { carga ->
                if (carga is Carga.Pronto) estadoAtual.update { it.copy(membros = carga.valor.membros) }
            }
        }
    }

    fun tentarDeNovo() = recarga.update { it + 1 }
    fun alternarHistorico() = estadoAtual.update { it.copy(mostrarHistorico = !it.mostrarHistorico) }

    fun mudarAjusteValor(texto: String) = estadoAtual.update {
        it.copy(ajusteValor = (if (texto.startsWith('-')) "-" else "") + texto.filter(Char::isDigit).take(7))
    }

    fun mudarAjusteMotivo(texto: String) = estadoAtual.update { it.copy(ajusteMotivo = texto.take(200)) }

    fun ajustar() {
        val e = estadoAtual.value
        val valor = e.ajusteValor.toIntOrNull()
        val motivo = e.ajusteMotivo.trim()
        val invalido = when {
            valor == null || valor == 0 -> R.string.premios_quantidade_invalida
            motivo.isEmpty() -> R.string.premios_motivo_vazio
            else -> null
        }
        if (invalido != null) {
            estadoAtual.update { it.copy(erro = MensagemDeErro.DoApp(invalido), aviso = null) }
            return
        }
        executar {
            recompensas.ajustarMoedas(PedidoDeAjuste(valor!!, motivo))
            estadoAtual.update { it.copy(ajusteValor = "", ajusteMotivo = "") }
            Aviso(R.string.premios_saldo_atualizado)
        }
    }

    fun pedirParaResgatar(premio: Premio) = estadoAtual.update { it.copy(confirmacao = Confirmacao.Resgatar(premio)) }
    fun pedirParaRemover(premio: Premio) = estadoAtual.update { it.copy(confirmacao = Confirmacao.Remover(premio)) }
    fun desistir() = estadoAtual.update { it.copy(confirmacao = null) }

    fun confirmar() {
        val confirmacao = estadoAtual.value.confirmacao ?: return
        estadoAtual.update { it.copy(confirmacao = null) }
        when (confirmacao) {
            is Confirmacao.Resgatar -> executar {
                Aviso(R.string.premios_resgatado, listOf(recompensas.resgatar(confirmacao.premio.id).saldo))
            }
            is Confirmacao.Remover -> executar {
                recompensas.excluirPremio(confirmacao.premio.id)
                null
            }
        }
    }

    fun abrirNovoPremio() = estadoAtual.update { it.copy(formulario = FormularioDePremio(aberto = true)) }

    fun editarPremio(premio: Premio) = estadoAtual.update {
        it.copy(formulario = FormularioDePremio(true, premio, premio.nome, premio.descricao.orEmpty(), premio.preco.toString()))
    }

    fun fecharFormulario() = mudarFormulario { it.copy(aberto = false, erro = null) }
    fun mudarNome(nome: String) = mudarFormulario { it.copy(nome = nome.take(100)) }
    fun mudarDescricao(descricao: String) = mudarFormulario { it.copy(descricao = descricao.take(300)) }
    fun mudarPreco(preco: String) = mudarFormulario { it.copy(preco = preco.filter(Char::isDigit).take(8)) }

    fun salvarPremio() {
        val f = estadoAtual.value.formulario
        val nome = f.nome.trim()
        val preco = f.preco.toIntOrNull()
        val invalido = when {
            nome.isEmpty() -> R.string.premios_nome_vazio
            preco == null || preco <= 0 -> R.string.premios_preco_invalido
            else -> null
        }
        if (invalido != null) {
            mudarFormulario { it.copy(erro = MensagemDeErro.DoApp(invalido)) }
            return
        }
        val editando = f.editando
        val pedido = NovoPremio(nome, f.descricao.trim(), preco!!, editando?.ativo ?: true)
        executar(aoFalhar = { erro -> mudarFormulario { it.copy(erro = erro) } }) {
            if (editando == null) recompensas.criarPremio(pedido) else recompensas.atualizarPremio(editando.id, pedido)
            mudarFormulario { FormularioDePremio() }
            null
        }
    }

    private fun mudarFormulario(mudar: (FormularioDePremio) -> FormularioDePremio) =
        estadoAtual.update { it.copy(formulario = mudar(it.formulario)) }

    /** Uma ação por vez; dando certo ou não, recarrega (o saldo pode ter mudado noutro aparelho). */
    private fun executar(
        aoFalhar: (MensagemDeErro) -> Unit = { erro -> estadoAtual.update { it.copy(erro = erro) } },
        acao: suspend () -> Aviso?,
    ) {
        if (estadoAtual.value.ocupado) return
        estadoAtual.update { it.copy(ocupado = true, erro = null, aviso = null, formulario = it.formulario.copy(erro = null)) }
        viewModelScope.launch {
            try {
                val aviso = acao()
                estadoAtual.update { it.copy(ocupado = false, aviso = aviso) }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(ocupado = false) }
                aoFalhar(mensagemDeErro(falha))
            }
            recarga.update { it + 1 }
        }
    }

    private suspend fun buscar(todos: Boolean): DadosDosPremios = coroutineScope {
        val saldo = async { recompensas.consultarSaldo() }
        val premios = async { recompensas.listarPremios(todos) }
        val resgates = async { recompensas.listarResgates() }
        DadosDosPremios(saldo.await(), premios.await(), resgates.await())
    }

    /** "28 de out., 10:00", como o AvisoDeGuardado do iOS. */
    private fun quandoGuardado(milissegundos: Long): String =
        DateTimeFormatter.ofPattern("d 'de' MMM, HH:mm", Locale.forLanguageTag("pt-BR"))
            .format(Instant.ofEpochMilli(milissegundos).atZone(relogio.zone))
}
