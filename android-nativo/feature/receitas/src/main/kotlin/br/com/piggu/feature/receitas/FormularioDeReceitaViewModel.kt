package br.com.piggu.feature.receitas

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.Lancamentos
import br.com.piggu.core.data.ListasParaLancar
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.model.Decimal
import br.com.piggu.core.model.JsonDoPiggu
import br.com.piggu.core.model.NovaReceita
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.formatos.TETO_DA_RECEITA
import br.com.piggu.core.ui.formatos.dataPedeConfirmacao
import br.com.piggu.core.ui.formatos.problemaDoValor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** A categoria que o formulário já traz escolhida: o nome no backend, não um texto da tela. */
internal const val CATEGORIA_INICIAL = "Salário"

data class EstadoDoFormularioDeReceita(
    /** aaaa-mm-dd. */
    val data: String,
    val descricao: String = "",
    val categoria: String = CATEGORIA_INICIAL,
    val categorias: List<String> = emptyList(),
    val valor: BigDecimal? = null,
    /** Entra sozinha todo mês no mesmo dia. */
    val repetir: Boolean = false,
    /** Só na recorrente: o salário que entra em duas vezes; o [valor] é a 1ª parcela (D-087). */
    val emDuasParcelas: Boolean = false,
    /** Como a pessoa digitou: o dia só é conferido ao lançar. */
    val segundoDia: String = "",
    val segundoValor: BigDecimal? = null,
    /** Nula segue o padrão do perfil. */
    val compartilhada: Boolean? = null,
    val compartilharPorPadrao: Boolean = false,
    /** Mora mais alguém na casa: só então aparece "Compartilhada". */
    val temCompanhia: Boolean = false,
    val moeda: String = "EUR",
    val salvando: Boolean = false,
    val erro: MensagemDeErro? = null,
    /** Lançou: a mensagem a entregar a quem abriu o formulário, uma vez só. */
    val lancada: Aviso? = null,
    /** aaaa-mm-dd de hoje, para a pergunta da data. */
    val hoje: String = data,
    /** Data futura ou de mais de um ano: o "Lançar mesmo assim?" fica aberto. */
    val confirmarData: Boolean = false,
) {
    /** Algo digitado: fechar a folha pergunta antes de descartar (BUG-016). */
    val temAlgoDigitado: Boolean get() = descricao.isNotBlank() || valor != null

    val diaDaReceita: Int get() = data.substring(8, 10).toInt()
    val compartilhadaEfetiva: Boolean get() = compartilhada ?: compartilharPorPadrao

    /** A escolhida entra na lista enquanto as categorias não chegam (como o Picker do iOS). */
    val opcoesDeCategoria: List<String> get() = if (categoria in categorias) categorias else listOf(categoria) + categorias
}

/**
 * Nova receita (formulario-de-receita.ts e NovaReceitaView do iOS), o mesmo na aba e no + do
 * app. Busca as categorias e a família ele mesmo, para abrir de qualquer aba (D-098).
 */
@HiltViewModel
class FormularioDeReceitaViewModel @Inject constructor(
    private val financas: ServicoDeFinancas,
    cache: CacheDeRespostas,
    listas: ListasParaLancar,
    sessao: Sessao,
    private val lancamentos: Lancamentos,
    relogio: Clock,
    private val estadoSalvo: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val estadoAtual = MutableStateFlow(
        EstadoDoFormularioDeReceita(
            data = LocalDate.now(relogio).toString(),
            compartilharPorPadrao = sessao.usuario?.compartilhamento?.compartilharPorPadrao ?: false,
            moeda = sessao.usuario?.preferencias?.moeda ?: "EUR",
        ),
    )
    val estado: StateFlow<EstadoDoFormularioDeReceita> = estadoAtual.asStateFlow()

    /** O último pedido enviado: tocar "Lançar" de novo sem mudar nada repete o idDoEnvio (D-192). */
    private var ultimoEnvio: NovaReceita? = null

    init {
        // O que estava digitado quando o sistema matou o app volta (BUG-016); cada mudança é guardada.
        estadoSalvo.get<String>(RASCUNHO)?.let { json ->
            runCatching { JsonDoPiggu.decodeFromString(RascunhoDaReceita.serializer(), json) }.getOrNull()?.let { r -> estadoAtual.update { r.aplicar(it) } }
        }
        viewModelScope.launch {
            estadoAtual.collect { estadoSalvo[RASCUNHO] = JsonDoPiggu.encodeToString(RascunhoDaReceita.serializer(), RascunhoDaReceita.de(it)) }
        }
        viewModelScope.launch {
            cache.carregar("categorias-de-receita", ListSerializer(String.serializer())) { financas.categoriasDeReceita() }.collect { carga ->
                if (carga is Carga.Pronto) estadoAtual.update { it.copy(categorias = carga.valor) }
            }
        }
        viewModelScope.launch {
            listas.familia().collect { carga ->
                if (carga is Carga.Pronto) estadoAtual.update { it.copy(temCompanhia = carga.valor.membros.size > 1) }
            }
        }
    }

    fun mudarValor(valor: BigDecimal?) = estadoAtual.update { it.copy(valor = valor) }
    fun mudarDescricao(descricao: String) = estadoAtual.update { it.copy(descricao = descricao.take(200)) }
    fun mudarCategoria(categoria: String) = estadoAtual.update { it.copy(categoria = categoria) }
    fun mudarData(data: String) = estadoAtual.update { it.copy(data = data, confirmarData = false) }
    fun corrigirData() = estadoAtual.update { it.copy(confirmarData = false) }
    fun mudarRepetir(repetir: Boolean) = estadoAtual.update { it.copy(repetir = repetir) }
    fun mudarEmDuasParcelas(duas: Boolean) = estadoAtual.update { it.copy(emDuasParcelas = duas) }
    fun mudarSegundoDia(dia: String) = estadoAtual.update { it.copy(segundoDia = dia.filter(Char::isDigit).take(2)) }
    fun mudarSegundoValor(valor: BigDecimal?) = estadoAtual.update { it.copy(segundoValor = valor) }
    fun mudarCompartilhada(compartilhada: Boolean) = estadoAtual.update { it.copy(compartilhada = compartilhada) }

    /** Quem abriu o formulário já mostrou a mensagem. */
    fun lancadaEntregue() = estadoAtual.update { it.copy(lancada = null) }

    /** @param dataConfirmada a pessoa já disse "Lançar" na pergunta da data. */
    fun lancar(dataConfirmada: Boolean = false) {
        val e = estadoAtual.value
        if (e.salvando) return
        estadoAtual.update { it.copy(confirmarData = false) }
        val descricao = e.descricao.trim()
        val valor = e.valor
        val dividir = e.repetir && e.emDuasParcelas
        val segundoDia = e.segundoDia.toIntOrNull()
        val segundoValor = e.segundoValor
        // Na ordem do site: descrição, valor, dia e valor da 2ª parcela.
        val doValor = problemaDoValor(valor, TETO_DA_RECEITA)
        val problema = when {
            descricao.isEmpty() -> R.string.receita_falta_descricao
            doValor != null -> doValor
            dividir && (segundoDia == null || segundoDia !in 1..31) -> R.string.receita_falta_segundo_dia
            dividir && segundoValor == null -> R.string.receita_falta_segundo_valor
            dividir -> problemaDoValor(segundoValor, TETO_DA_RECEITA)
            else -> null
        }
        if (problema != null) {
            estadoAtual.update { it.copy(erro = MensagemDeErro.DoApp(problema)) }
            return
        }
        checkNotNull(valor)
        val data = runCatching { LocalDate.parse(e.data) }.getOrNull()
        if (!dataConfirmada && data != null && dataPedeConfirmacao(data, LocalDate.parse(e.hoje))) {
            estadoAtual.update { it.copy(confirmarData = true, erro = null) }
            return
        }
        val semId = NovaReceita(
            data = e.data,
            descricao = descricao,
            categoria = e.categoria,
            valor = valor,
            compartilhado = e.temCompanhia && e.compartilhadaEfetiva,
            recorrente = e.repetir,
            segundoDia = segundoDia.takeIf { dividir },
            segundoValor = segundoValor.takeIf { dividir },
        )
        // Sem resposta (5xx, tempo esgotado) o servidor pode ter gravado: o mesmo pedido leva o mesmo id.
        val pedido = semId.copy(idDoEnvio = ultimoEnvio?.takeIf { it.copy(idDoEnvio = null) == semId }?.idDoEnvio ?: UUID.randomUUID().toString())
        ultimoEnvio = pedido
        estadoAtual.update { it.copy(salvando = true, erro = null) }
        viewModelScope.launch {
            try {
                financas.lancarReceita(pedido)
                ultimoEnvio = null
                val mensagem = when {
                    dividir -> Aviso(R.string.receita_lancada_duas_parcelas, listOf(e.diaDaReceita, segundoDia!!))
                    e.repetir -> Aviso(R.string.receita_lancada_recorrente, listOf(e.diaDaReceita))
                    else -> Aviso(R.string.receita_lancada)
                }
                // Limpa como o site: a data e a categoria ficam para a próxima.
                estadoAtual.update {
                    it.copy(
                        descricao = "", valor = null, repetir = false, emDuasParcelas = false, segundoDia = "",
                        segundoValor = null, compartilhada = null, salvando = false, lancada = mensagem,
                    )
                }
                lancamentos.avisar()
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(salvando = false, erro = mensagemDeErro(falha)) }
            }
        }
    }
}

private const val RASCUNHO = "rascunho-da-receita"

/** O que a pessoa digitou na receita, guardado no SavedStateHandle (BUG-016). */
@Serializable
internal data class RascunhoDaReceita(
    val data: String,
    val descricao: String = "",
    val categoria: String = CATEGORIA_INICIAL,
    val valor: Decimal? = null,
    val repetir: Boolean = false,
    val emDuasParcelas: Boolean = false,
    val segundoDia: String = "",
    val segundoValor: Decimal? = null,
    val compartilhada: Boolean? = null,
) {
    fun aplicar(e: EstadoDoFormularioDeReceita) = e.copy(
        data = data, descricao = descricao, categoria = categoria, valor = valor, repetir = repetir,
        emDuasParcelas = emDuasParcelas, segundoDia = segundoDia, segundoValor = segundoValor, compartilhada = compartilhada,
    )

    companion object {
        fun de(e: EstadoDoFormularioDeReceita) = RascunhoDaReceita(
            e.data, e.descricao, e.categoria, e.valor, e.repetir, e.emDuasParcelas, e.segundoDia, e.segundoValor, e.compartilhada,
        )
    }
}
