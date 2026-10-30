package br.com.piggu.feature.listas.compras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.model.ItemDeCompra
import br.com.piggu.core.model.ListaDeCompras
import br.com.piggu.core.model.NovoItemDeCompra
import br.com.piggu.core.model.ProdutoDoCatalogo
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

data class EstadoDasCompras(
    val carga: Carga<List<ItemDeCompra>> = Carga.Carregando,
    /** Quando foi guardado o que aparece, se a atualização falhou ("28 de out., 10:00"). */
    val guardadoEm: String? = null,
    val conectado: Boolean = true,
    /** A aba aberta: Compras ou Desejos. */
    val lista: ListaDeCompras = ListaDeCompras.COMPRAS,
    val item: String = "",
    val quantidade: String = "",
    val busca: String = "",
    val buscando: Boolean = false,
    val resultados: List<ProdutoDoCatalogo> = emptyList(),
    val erro: MensagemDeErro? = null,
) {
    private val daLista: List<ItemDeCompra> get() = (carga as? Carga.Pronto)?.valor.orEmpty().filter { it.lista == lista }
    val pendentes: List<ItemDeCompra> get() = daLista.filter { !it.comprado }
    val comprados: List<ItemDeCompra> get() = daLista.filter { it.comprado }
}

/**
 * Compras (features/shopping e ComprasView do iOS): as listas de compras e de desejos. A busca
 * no catálogo aberto, digitada ou pelo leitor de código de barras, preenche marca, imagem e
 * código sem digitar. O backend só aceita imagem por HTTPS: o resto chega vazio e fica sem foto.
 */
@HiltViewModel
class ComprasViewModel internal constructor(
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
    private val estadoAtual = MutableStateFlow(EstadoDasCompras())
    val estado: StateFlow<EstadoDasCompras> = estadoAtual.asStateFlow()

    init {
        viewModelScope.launch {
            combine(recarga, conectado) { _, conectado -> conectado }.collectLatest { conectado ->
                estadoAtual.update { it.copy(conectado = conectado) }
                cache.carregar("compras", ListSerializer(ItemDeCompra.serializer())) { estiloDeVida.listarCompras() }.collect { carga ->
                    val guardadaEm = (carga as? Carga.Pronto)?.guardadaEm
                    estadoAtual.update { it.copy(carga = carga, guardadoEm = guardadaEm?.let(::quandoGuardado)) }
                }
            }
        }
    }

    fun tentarDeNovo() = recarga.update { it + 1 }
    fun trocarLista(lista: ListaDeCompras) = estadoAtual.update { it.copy(lista = lista) }
    fun mudarItem(item: String) = estadoAtual.update { it.copy(item = item.take(150)) }
    fun mudarQuantidade(quantidade: String) = estadoAtual.update { it.copy(quantidade = quantidade.take(50)) }
    fun mudarBusca(busca: String) = estadoAtual.update { it.copy(busca = busca.take(100)) }

    fun adicionar() {
        val e = estadoAtual.value
        val item = e.item.trim()
        if (item.isEmpty()) {
            estadoAtual.update { it.copy(erro = MensagemDeErro.DoApp(R.string.compras_falta_item)) }
            return
        }
        inserir(NovoItemDeCompra(item, e.quantidade.trim(), e.lista)) { it.copy(item = "", quantidade = "") }
    }

    fun adicionarDoCatalogo(produto: ProdutoDoCatalogo) = inserir(
        NovoItemDeCompra(produto.nome, produto.quantidade, estadoAtual.value.lista, produto.marca, produto.imagem, produto.codigo),
    ) { it.copy(resultados = emptyList(), busca = "") }

    fun buscar() {
        val termo = estadoAtual.value.busca.trim()
        if (termo.length < 2) {
            estadoAtual.update { it.copy(erro = MensagemDeErro.DoApp(R.string.compras_busca_curta)) }
            return
        }
        if (estadoAtual.value.buscando) return
        estadoAtual.update { it.copy(erro = null, buscando = true) }
        viewModelScope.launch {
            try {
                val produtos = estiloDeVida.buscarNoCatalogo(termo)
                estadoAtual.update { it.copy(resultados = produtos, buscando = false) }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(falha), buscando = false) }
            }
        }
    }

    /** O leitor de código de barras leu um código: busca por ele no catálogo. */
    fun codigoLido(codigo: String) {
        mudarBusca(codigo.trim())
        buscar()
    }

    /** O leitor do Google Play services não abriu (sem o módulo, sem Play services...). */
    fun leitorIndisponivel() = estadoAtual.update { it.copy(erro = MensagemDeErro.DoApp(R.string.compras_leitor_indisponivel)) }

    /** Marca na hora e desfaz se o servidor recusar: esperar a resposta deixaria a lista lenta no mercado. */
    fun alternarComprado(item: ItemDeCompra) {
        val desejado = !item.comprado
        aplicarComprado(item.id, desejado)
        viewModelScope.launch {
            try {
                estiloDeVida.marcarComprado(item.id, desejado)
            } catch (falha: ErroDaApi) {
                aplicarComprado(item.id, !desejado)
                estadoAtual.update { it.copy(erro = mensagemDeErro(falha)) }
            }
        }
    }

    fun excluir(item: ItemDeCompra) {
        viewModelScope.launch {
            try {
                estiloDeVida.excluirItemDeCompra(item.id)
                mudarLista { lista -> lista.filter { it.id != item.id } }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(falha)) }
            }
        }
    }

    /** Cria o item e, dando certo, põe no topo e limpa o que [limpar] disser. */
    private fun inserir(novo: NovoItemDeCompra, limpar: (EstadoDasCompras) -> EstadoDasCompras) {
        estadoAtual.update { it.copy(erro = null) }
        viewModelScope.launch {
            try {
                val criado = estiloDeVida.criarItemDeCompra(novo)
                mudarLista { listOf(criado) + it }
                estadoAtual.update(limpar)
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(falha)) }
            }
        }
    }

    private fun aplicarComprado(id: String, comprado: Boolean) =
        mudarLista { lista -> lista.map { if (it.id == id) it.copy(comprado = comprado) else it } }

    private fun mudarLista(mudar: (List<ItemDeCompra>) -> List<ItemDeCompra>) = estadoAtual.update { e ->
        val pronto = e.carga as? Carga.Pronto ?: return@update e
        e.copy(carga = pronto.copy(valor = mudar(pronto.valor)))
    }

    /** "28 de out., 10:00": quando o guardado foi gravado. */
    private fun quandoGuardado(milissegundos: Long): String =
        DateTimeFormatter.ofPattern("d 'de' MMM, HH:mm", Locale.forLanguageTag("pt-BR"))
            .format(Instant.ofEpochMilli(milissegundos).atZone(relogio.zone))
}
