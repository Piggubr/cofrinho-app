package br.com.piggu.feature.metas

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.data.mesAtual
import br.com.piggu.core.model.MetaDoMes
import br.com.piggu.core.model.NovaMetaDoMes
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeEstiloDeVida
import br.com.piggu.core.network.mensagemDeErro
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/** O campo que barrou o "Salvar meta", para marcar como inválido. */
enum class CampoDaMeta { Titulo, Alvo, Atual }

/** Criar ou editar uma meta (o cartão do site, numa folha como no iOS). */
data class FormularioDeMeta(
    val aberto: Boolean = false,
    /** Id da meta em edição; nulo cria uma nova. */
    val editando: String? = null,
    val titulo: String = "",
    /** Como a pessoa digitou (só dígitos): conferido ao salvar. */
    val alvo: String = "",
    val atual: String = "0",
    val faltou: CampoDaMeta? = null,
    val erro: MensagemDeErro? = null,
)

data class EstadoDasMetas(
    /** aaaa-mm: as metas valem só para o mês. */
    val mes: String,
    val metas: Carga<List<MetaDoMes>> = Carga.Carregando,
    /** Quando foi guardado o que aparece, se a atualização falhou ("28 de out., 10:00"). */
    val guardadoEm: String? = null,
    val conectado: Boolean = true,
    val salvando: Boolean = false,
    val erro: MensagemDeErro? = null,
    /** A meta que espera o "Apagar" (o confirm do site). */
    val apagando: MetaDoMes? = null,
    val formulario: FormularioDeMeta = FormularioDeMeta(),
) {
    val lista: List<MetaDoMes> get() = (metas as? Carga.Pronto)?.valor.orEmpty()

    val concluidas: Int get() = lista.count { it.concluida }

    /** Concluídas sobre o total, de 0 a 100 (percentualConcluido): uma meta grande não esconde as pequenas. */
    val percentual: Int get() = if (lista.isEmpty()) 0 else (concluidas * 100.0 / lista.size).roundToInt()

    @get:StringRes
    val mensagem: Int
        get() = when {
            lista.isEmpty() -> R.string.metas_mensagem_vazia
            concluidas == lista.size -> R.string.metas_mensagem_todas
            else -> R.string.metas_mensagem_ritmo
        }

    /** De 0 a 1, para a barra da meta. */
    fun progresso(meta: MetaDoMes): Float = (meta.atual.toFloat() / meta.alvo.coerceAtLeast(1)).coerceIn(0f, 1f)

    /**
     * O texto curto para mandar por mensagem (textoParaCompartilhar do site): só título e
     * progresso, nada de dinheiro. [linha] é o formato de metas_compartilhar_linha.
     */
    fun textoParaCompartilhar(cabecalho: String, linha: String): String =
        (listOf(cabecalho) + lista.map { linha.format(if (it.concluida) "✅" else "⏳", it.titulo, it.atual, it.alvo) })
            .joinToString("\n")
}

/**
 * Minhas metas do mês (features/minhas-metas e MinhasMetasView do iOS): metas pessoais com alvo e
 * progresso. São só de quem criou (o backend filtra) e valem para o mês: o mês é lido a cada
 * carga, então quando ele vira a lista começa vazia de novo.
 */
@HiltViewModel
class MinhasMetasViewModel internal constructor(
    private val estiloDeVida: ServicoDeEstiloDeVida,
    private val cache: CacheDeRespostas,
    private val relogio: Clock,
    conectado: StateFlow<Boolean>,
) : ViewModel() {
    @Inject constructor(
        estiloDeVida: ServicoDeEstiloDeVida,
        cache: CacheDeRespostas,
        relogio: Clock,
        rede: MonitorDeRede,
    ) : this(estiloDeVida, cache, relogio, rede.conectado)

    private val recarga = MutableStateFlow(0)
    private val estadoAtual = MutableStateFlow(EstadoDasMetas(relogio.mesAtual()))
    val estado: StateFlow<EstadoDasMetas> = estadoAtual.asStateFlow()

    init {
        viewModelScope.launch {
            combine(recarga, conectado) { _, conectado -> conectado }.collectLatest { conectado ->
                val mes = relogio.mesAtual()
                estadoAtual.update { it.copy(mes = mes, conectado = conectado) }
                // O filtro pelo mês segura a meta do mês passado, se o servidor mandar.
                cache.carregar("metas-do-mes/$mes", ListSerializer(MetaDoMes.serializer())) {
                    estiloDeVida.listarMetasDoMes(mes).filter { it.mes == null || it.mes == mes }
                }.collect { carga ->
                    val guardadaEm = (carga as? Carga.Pronto)?.guardadaEm
                    estadoAtual.update { it.copy(metas = carga, guardadoEm = guardadaEm?.let(::quandoGuardado)) }
                }
            }
        }
    }

    fun tentarDeNovo() = recarga.update { it + 1 }

    fun abrirNova() = estadoAtual.update { it.copy(formulario = FormularioDeMeta(aberto = true), erro = null) }

    fun editar(meta: MetaDoMes) = estadoAtual.update {
        it.copy(
            formulario = FormularioDeMeta(true, meta.id, meta.titulo, meta.alvo.toString(), meta.atual.toString()),
            erro = null,
        )
    }

    fun fecharFormulario() = estadoAtual.update { it.copy(formulario = FormularioDeMeta()) }

    fun mudarTitulo(titulo: String) = mudarFormulario { it.copy(titulo = titulo.take(80)) }
    fun mudarAlvo(alvo: String) = mudarFormulario { it.copy(alvo = alvo.filter(Char::isDigit).take(7)) }
    fun mudarAtual(atual: String) = mudarFormulario { it.copy(atual = atual.filter(Char::isDigit).take(7)) }

    fun salvar() {
        val f = estadoAtual.value.formulario
        if (estadoAtual.value.salvando) return
        val titulo = f.titulo.trim()
        val alvo = f.alvo.toIntOrNull()
        val atual = f.atual.ifEmpty { "0" }.toIntOrNull()
        when {
            titulo.isEmpty() -> recusar(CampoDaMeta.Titulo, R.string.metas_falta_nome)
            alvo == null || alvo < 1 -> recusar(CampoDaMeta.Alvo, R.string.metas_alvo_invalido)
            atual == null || atual < 0 -> recusar(CampoDaMeta.Atual, R.string.metas_atual_invalido)
            else -> gravar(f.editando, NovaMetaDoMes(titulo, alvo, atual), fechar = true)
        }
    }

    /** O atalho do dia a dia: mais um passo sem abrir o formulário. */
    fun avancar(meta: MetaDoMes) = gravar(meta.id, NovaMetaDoMes(meta.titulo, meta.alvo, meta.atual + 1), fechar = false)

    fun pedirParaApagar(meta: MetaDoMes) = estadoAtual.update { it.copy(apagando = meta) }
    fun desistir() = estadoAtual.update { it.copy(apagando = null) }

    fun confirmar() {
        val meta = estadoAtual.value.apagando ?: return
        estadoAtual.update { it.copy(apagando = null) }
        viewModelScope.launch {
            try {
                estiloDeVida.excluirMetaDoMes(meta.id)
                mudarLista { lista -> lista.filter { it.id != meta.id } }
                estadoAtual.update { it.copy(erro = null) }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(falha)) }
            }
        }
    }

    private fun gravar(id: String?, meta: NovaMetaDoMes, fechar: Boolean) {
        if (estadoAtual.value.salvando) return
        estadoAtual.update { it.copy(salvando = true, erro = null) }
        viewModelScope.launch {
            try {
                val salva = if (id != null) estiloDeVida.atualizarMetaDoMes(id, meta) else estiloDeVida.criarMetaDoMes(meta)
                mudarLista { lista -> if (id != null) lista.map { if (it.id == id) salva else it } else lista + salva }
                estadoAtual.update { it.copy(salvando = false, formulario = if (fechar) FormularioDeMeta() else it.formulario) }
            } catch (falha: ErroDaApi) {
                // Com a folha aberta, o erro aparece nela; sem, na faixa da tela.
                estadoAtual.update {
                    if (it.formulario.aberto) {
                        it.copy(salvando = false, formulario = it.formulario.copy(erro = mensagemDeErro(falha), faltou = null))
                    } else {
                        it.copy(salvando = false, erro = mensagemDeErro(falha))
                    }
                }
            }
        }
    }

    private fun recusar(campo: CampoDaMeta, @StringRes texto: Int) =
        mudarFormulario { it.copy(faltou = campo, erro = MensagemDeErro.DoApp(texto)) }

    private fun mudarFormulario(mudar: (FormularioDeMeta) -> FormularioDeMeta) =
        estadoAtual.update { it.copy(formulario = mudar(it.formulario)) }

    private fun mudarLista(mudar: (List<MetaDoMes>) -> List<MetaDoMes>) = estadoAtual.update { e ->
        val pronto = e.metas as? Carga.Pronto ?: return@update e
        e.copy(metas = pronto.copy(valor = mudar(pronto.valor)))
    }

    /** "28 de out., 10:00": quando o guardado foi gravado. */
    private fun quandoGuardado(milissegundos: Long): String =
        DateTimeFormatter.ofPattern("d 'de' MMM, HH:mm", Locale.forLanguageTag("pt-BR"))
            .format(Instant.ofEpochMilli(milissegundos).atZone(relogio.zone))
}
