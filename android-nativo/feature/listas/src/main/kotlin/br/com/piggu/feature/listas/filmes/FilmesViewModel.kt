package br.com.piggu.feature.listas.filmes

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.ListasParaLancar
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.model.Filme
import br.com.piggu.core.model.FilmeDoCatalogo
import br.com.piggu.core.model.MembroDaFamilia
import br.com.piggu.core.model.PedidoDeAvaliacao
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeEstiloDeVida
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.feature.listas.R
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

/** Um gênero do TMDB do sorteio: o id ("" é qualquer um) e o nome na tela. */
data class Genero(val id: String, @param:StringRes val nome: Int)

/** Os gêneros do TMDB que cabem num sorteio de noite de filme (GENEROS de movies.ts). */
val GENEROS = listOf(
    Genero("", R.string.filmes_genero_qualquer),
    Genero("28", R.string.filmes_genero_acao),
    Genero("35", R.string.filmes_genero_comedia),
    Genero("18", R.string.filmes_genero_drama),
    Genero("27", R.string.filmes_genero_terror),
    Genero("10749", R.string.filmes_genero_romance),
    Genero("878", R.string.filmes_genero_ficcao),
    Genero("16", R.string.filmes_genero_animacao),
    Genero("53", R.string.filmes_genero_suspense),
)

/** A nota de outra pessoa da família; [nome] nulo é quem já saiu ("Ex-membro"). */
data class NotaDeOutro(val nome: String?, val nota: Int)

data class EstadoDosFilmes(
    val carga: Carga<List<Filme>> = Carga.Carregando,
    /** Quando foi guardado o que aparece, se a atualização falhou ("28 de out., 10:00"). */
    val guardadoEm: String? = null,
    val conectado: Boolean = true,
    /** O id de quem está usando: a nota dele é a das estrelas tocáveis. */
    val eu: String = "",
    val membros: List<MembroDaFamilia> = emptyList(),
    val genero: String = "",
    val sorteando: Boolean = false,
    val sorteado: FilmeDoCatalogo? = null,
    val busca: String = "",
    val buscando: Boolean = false,
    val resultados: List<FilmeDoCatalogo> = emptyList(),
    val erro: MensagemDeErro? = null,
    /** O filme que espera o "Tirar da lista" (o confirm do site). */
    val tirando: Filme? = null,
) {
    val filmes: List<Filme> get() = (carga as? Carga.Pronto)?.valor.orEmpty()
    val paraVer: List<Filme> get() = filmes.filter { !it.assistido }
    val assistidos: List<Filme> get() = filmes.filter { it.assistido }

    /** 0 enquanto a pessoa não avaliou. */
    fun minhaNota(filme: Filme): Int = filme.avaliacoes[eu] ?: 0

    /** As notas dos outros, separadas: a média só faria sentido depois que todos avaliassem (movies.ts). */
    fun outrasNotas(filme: Filme): List<NotaDeOutro> =
        filme.avaliacoes.filterKeys { it != eu }.toSortedMap().map { (pessoa, nota) ->
            NotaDeOutro(membros.firstOrNull { it.id == pessoa }?.nome, nota)
        }
}

/**
 * Filmes (features/movies e FilmesView do iOS): a lista da família, buscar e sortear no catálogo
 * (o TMDB, pelo backend), marcar como assistido e a nota de cada pessoa, de 1 a 5.
 */
@HiltViewModel
class FilmesViewModel internal constructor(
    private val estiloDeVida: ServicoDeEstiloDeVida,
    private val cache: CacheDeRespostas,
    listas: ListasParaLancar,
    sessao: Sessao,
    private val relogio: Clock,
    conectado: StateFlow<Boolean>,
) : ViewModel() {
    @Inject constructor(
        estiloDeVida: ServicoDeEstiloDeVida,
        cache: CacheDeRespostas,
        listas: ListasParaLancar,
        sessao: Sessao,
        relogio: Clock,
        rede: MonitorDeRede,
    ) : this(estiloDeVida, cache, listas, sessao, relogio, rede.conectado)

    private val recarga = MutableStateFlow(0)
    private val estadoAtual = MutableStateFlow(EstadoDosFilmes(eu = sessao.usuario?.id.orEmpty()))
    val estado: StateFlow<EstadoDosFilmes> = estadoAtual.asStateFlow()

    init {
        viewModelScope.launch {
            combine(recarga, conectado) { _, conectado -> conectado }.collectLatest { conectado ->
                estadoAtual.update { it.copy(conectado = conectado) }
                cache.carregar("filmes", ListSerializer(Filme.serializer())) { estiloDeVida.listarFilmes() }.collect { carga ->
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

    fun mudarGenero(genero: String) = estadoAtual.update { it.copy(genero = genero) }
    fun mudarBusca(busca: String) = estadoAtual.update { it.copy(busca = busca.take(100)) }

    fun sortear() {
        if (estadoAtual.value.sorteando) return
        estadoAtual.update { it.copy(sorteando = true, erro = null) }
        viewModelScope.launch {
            try {
                val filme = estiloDeVida.sortearFilme(estadoAtual.value.genero.ifEmpty { null })
                estadoAtual.update { it.copy(sorteando = false, sorteado = filme) }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(sorteando = false, erro = mensagemDeErro(falha)) }
            }
        }
    }

    fun buscar() {
        val e = estadoAtual.value
        if (e.buscando) return
        val termo = e.busca.trim()
        if (termo.length < 2) {
            estadoAtual.update { it.copy(erro = MensagemDeErro.DoApp(R.string.filmes_busca_curta)) }
            return
        }
        estadoAtual.update { it.copy(buscando = true, erro = null) }
        viewModelScope.launch {
            try {
                val filmes = estiloDeVida.buscarFilmes(termo)
                estadoAtual.update { it.copy(buscando = false, resultados = filmes) }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(buscando = false, erro = mensagemDeErro(falha)) }
            }
        }
    }

    fun adicionar(filme: FilmeDoCatalogo) {
        estadoAtual.update { it.copy(erro = null) }
        viewModelScope.launch {
            try {
                val criado = estiloDeVida.adicionarFilme(filme)
                mudarLista { listOf(criado) + it }
                estadoAtual.update { it.copy(resultados = emptyList(), sorteado = null, busca = "") }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(falha)) }
            }
        }
    }

    fun alternarAssistido(filme: Filme) = substituir { estiloDeVida.marcarFilmeAssistido(filme.id, !filme.assistido) }

    fun avaliar(filme: Filme, nota: Int) = substituir { estiloDeVida.avaliarFilme(filme.id, PedidoDeAvaliacao(nota.coerceIn(1, 5))) }

    fun pedirParaTirar(filme: Filme) = estadoAtual.update { it.copy(tirando = filme) }
    fun desistir() = estadoAtual.update { it.copy(tirando = null) }

    fun confirmar() {
        val filme = estadoAtual.value.tirando ?: return
        estadoAtual.update { it.copy(tirando = null) }
        viewModelScope.launch {
            try {
                estiloDeVida.excluirFilme(filme.id)
                mudarLista { lista -> lista.filter { it.id != filme.id } }
                estadoAtual.update { it.copy(erro = null) }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(falha)) }
            }
        }
    }

    private fun substituir(acao: suspend () -> Filme) {
        viewModelScope.launch {
            try {
                val atualizado = acao()
                mudarLista { lista -> lista.map { if (it.id == atualizado.id) atualizado else it } }
                estadoAtual.update { it.copy(erro = null) }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(falha)) }
            }
        }
    }

    private fun mudarLista(mudar: (List<Filme>) -> List<Filme>) = estadoAtual.update { e ->
        val pronto = e.carga as? Carga.Pronto ?: return@update e
        e.copy(carga = pronto.copy(valor = mudar(pronto.valor)))
    }

    /** "28 de out., 10:00": quando o guardado foi gravado. */
    private fun quandoGuardado(milissegundos: Long): String =
        DateTimeFormatter.ofPattern("d 'de' MMM, HH:mm", Locale.forLanguageTag("pt-BR"))
            .format(Instant.ofEpochMilli(milissegundos).atZone(relogio.zone))
}
