package br.com.piggu.feature.assistente.busca

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.modulosLigados
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.model.Filme
import br.com.piggu.core.model.Gasto
import br.com.piggu.core.model.ItemDeCompra
import br.com.piggu.core.model.ListaDeCompras
import br.com.piggu.core.model.Lugar
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.ServicoDeEstiloDeVida
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.core.ui.formatos.dataCurta
import br.com.piggu.core.ui.formatos.formatarMoeda
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer
import javax.inject.Inject

/** Espera depois da última letra antes de buscar os gastos no servidor (debounceTime do site). */
const val ESPERA_DA_BUSCA_MS = 250L

/** Gastos, lugares, filmes e compras só a partir de 2 letras; as telas, desde a primeira. */
const val LETRAS_MINIMAS = 2

/** Até 8 de cada lista da família, como no site. */
private const val POR_LISTA = 8

enum class GrupoDaBusca { Telas, Gastos, Lugares, Filmes, Compras }

/** Aonde um resultado leva: um caminho do app ou um gasto no mês dele. */
sealed interface DestinoDaBusca {
    data class Caminho(val caminho: String) : DestinoDaBusca
    data class Gasto(val id: String, val mes: String) : DestinoDaBusca
}

data class ResultadoDaBusca(
    val grupo: GrupoDaBusca,
    val icone: Icone,
    val titulo: String,
    val detalhe: String,
    val destino: DestinoDaBusca,
)

data class EstadoDaBusca(
    val termo: String = "",
    val resultados: List<ResultadoDaBusca> = emptyList(),
) {
    /** "Nada encontrado", só depois do mínimo de letras (busca-global.ts). */
    val nadaEncontrado: Boolean get() = termo.trim().length >= LETRAS_MINIMAS && resultados.isEmpty()
}

private data class Listas(
    val lugares: List<Lugar> = emptyList(),
    val filmes: List<Filme> = emptyList(),
    val compras: List<ItemDeCompra> = emptyList(),
)

/** Sem acento e em minúsculas: "Pão" acha "pao". */
internal fun normalizar(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/**
 * Busca global (busca-global.ts): telas, gastos de todos os meses (no servidor, com espera e mínimo
 * de letras) e lugares, filmes e compras, filtrados aqui e só dos módulos ligados.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class BuscaViewModel @Inject constructor(
    private val sessao: Sessao,
    private val financas: ServicoDeFinancas,
    private val estiloDeVida: ServicoDeEstiloDeVida,
) : ViewModel() {
    private val termo = MutableStateFlow("")
    private val telas = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    private val gastos = MutableStateFlow<List<Gasto>>(emptyList())
    private val listas = MutableStateFlow(Listas())

    val estado: StateFlow<EstadoDaBusca> = combine(termo, telas, gastos, listas) { termo, telas, gastos, listas ->
        EstadoDaBusca(termo, resultados(termo, telas, gastos, listas))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EstadoDaBusca())

    init {
        viewModelScope.launch {
            termo.debounce(ESPERA_DA_BUSCA_MS).collectLatest { digitado ->
                val limpo = digitado.trim()
                gastos.value = if (limpo.length < LETRAS_MINIMAS) emptyList() else ouVazio { financas.buscarGastos(limpo) }
            }
        }
    }

    /** As telas que a pessoa vê no menu: (rótulo, caminho). */
    fun mudarTelas(telas: List<Pair<String, String>>) {
        this.telas.value = telas
    }

    /** Ao abrir: campo limpo e as listas da família de novo, só dos módulos ligados. */
    fun abrir() {
        termo.value = ""
        viewModelScope.launch {
            val ligados = sessao.usuario?.modulosLigados
            fun ligado(modulo: String) = ligados == null || modulo in ligados
            listas.value = coroutineScope {
                val lugares = async { if (ligado("lugares")) ouVazio { estiloDeVida.listarLugares() } else emptyList() }
                val filmes = async { if (ligado("filmes")) ouVazio { estiloDeVida.listarFilmes() } else emptyList() }
                val compras = async { if (ligado("compras")) ouVazio { estiloDeVida.listarCompras() } else emptyList() }
                Listas(lugares.await(), filmes.await(), compras.await())
            }
        }
    }

    fun fechar() {
        gastos.value = emptyList()
    }

    fun digitar(termo: String) {
        this.termo.value = termo.take(TERMO_MAXIMO)
    }

    private fun resultados(termo: String, telas: List<Pair<String, String>>, gastos: List<Gasto>, listas: Listas): List<ResultadoDaBusca> {
        val busca = normalizar(termo.trim())
        if (busca.isEmpty()) return emptyList()
        fun tem(vararg textos: String?) = textos.any { it != null && normalizar(it).contains(busca) }
        val dasTelas = telas.filter { tem(it.first) }.map { (rotulo, caminho) ->
            ResultadoDaBusca(GrupoDaBusca.Telas, Icone.Grade, rotulo, "", DestinoDaBusca.Caminho(caminho))
        }
        if (busca.length < LETRAS_MINIMAS) return dasTelas
        val moeda = sessao.usuario?.preferencias?.moeda
        val dosGastos = gastos.map {
            ResultadoDaBusca(
                GrupoDaBusca.Gastos, Icone.Carteira, it.item,
                "${dataCurta(it.data)} · ${formatarMoeda(it.valor, moeda)}",
                DestinoDaBusca.Gasto(it.id, it.data.take(7)),
            )
        }
        val lugares = listas.lugares
            .filter { tem(it.nome, it.localizacao, it.categoria) }.take(POR_LISTA)
            .map { ResultadoDaBusca(GrupoDaBusca.Lugares, Icone.Local, it.nome, it.localizacao.orEmpty(), DestinoDaBusca.Caminho("/lugares")) }
        val filmes = listas.filmes
            .filter { tem(it.titulo) }.take(POR_LISTA)
            .map { ResultadoDaBusca(GrupoDaBusca.Filmes, Icone.Filme, it.titulo, it.ano.orEmpty(), DestinoDaBusca.Caminho("/filmes")) }
        val compras = listas.compras
            .filter { tem(it.item, it.marca) }.take(POR_LISTA)
            .map { ResultadoDaBusca(GrupoDaBusca.Compras, Icone.Carrinho, it.item, nomeDaLista(it.lista), DestinoDaBusca.Caminho("/compras")) }
        return dasTelas + dosGastos + lugares + filmes + compras
    }

    private companion object {
        /** O maxlength do campo do site. */
        const val TERMO_MAXIMO = 100

        /** "Compras" ou "Desejos", o nome da lista como a API manda. */
        fun nomeDaLista(lista: ListaDeCompras) =
            if (lista == ListaDeCompras.DESCONHECIDO) "" else lista.name.lowercase().replaceFirstChar(Char::uppercaseChar)

        /** Como o catchError do site: uma lista que falha só não aparece. */
        suspend fun <T> ouVazio(buscar: suspend () -> List<T>): List<T> = try {
            buscar()
        } catch (_: ErroDaApi) {
            emptyList()
        }
    }
}
