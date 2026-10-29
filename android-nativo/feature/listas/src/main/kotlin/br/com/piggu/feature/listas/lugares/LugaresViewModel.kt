package br.com.piggu.feature.listas.lugares

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.model.Lugar
import br.com.piggu.core.model.NovoLugar
import br.com.piggu.core.model.PedidoDeNome
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeEstiloDeVida
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.feature.listas.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.net.URLEncoder
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import javax.inject.Inject

/** O que a tela mostra, buscado junto (o forkJoin do site) e guardado numa chave só ("lugares"). */
@Serializable
data class DadosDosLugares(val lugares: List<Lugar>, val marcadores: List<String>)

/** O campo que barrou o "Salvar" ou o "Adicionar marcador", para marcar como inválido. */
enum class CampoDoLugar { Nome, Marcador }

/** Novo lugar ou editar um, numa folha (no site é um cartão acima da lista). */
data class FormularioDeLugar(
    val aberto: Boolean = false,
    /** Nulo ao criar. */
    val editando: Lugar? = null,
    val nome: String = "",
    val categoria: String = "",
    val localizacao: String = "",
    /** aaaa-mm-dd: a data da visita. */
    val data: String = "",
    val valor: BigDecimal? = null,
    val nota: Int = 5,
    val comentario: String = "",
    val marcacoes: List<String> = emptyList(),
    /** A foto nova, já reduzida, em JPEG base64 (o que a API recebe). Sem ela, fica a que já estava. */
    val foto: String? = null,
    val lendoFoto: Boolean = false,
    val novoMarcador: String = "",
    val salvando: Boolean = false,
    val faltou: CampoDoLugar? = null,
    val erro: MensagemDeErro? = null,
)

data class EstadoDosLugares(
    val carga: Carga<DadosDosLugares> = Carga.Carregando,
    /** Quando foi guardado o que aparece, se a atualização falhou ("28 de out., 10:00"). */
    val guardadoEm: String? = null,
    val conectado: Boolean = true,
    val moeda: String = "EUR",
    val formulario: FormularioDeLugar = FormularioDeLugar(),
    /** Erro de apagar, ou de abrir o mapa. */
    val erro: MensagemDeErro? = null,
    /** O lugar que espera o "Apagar" (o confirm do site). */
    val apagando: Lugar? = null,
) {
    val dados: DadosDosLugares? get() = (carga as? Carga.Pronto)?.valor
}

/**
 * O endereço `geo:` do "Abrir no mapa": a busca pelo nome e pela localização escrita, como o
 * MKLocalSearch do iOS. O Lugar não tem coordenadas; sem localização, não há o que procurar.
 */
fun enderecoNoMapa(lugar: Lugar): String? {
    val onde = lugar.localizacao?.trim().orEmpty()
    if (onde.isEmpty()) return null
    return "geo:0,0?q=" + URLEncoder.encode("${lugar.nome}, $onde", "UTF-8").replace("+", "%20")
}

/**
 * Lugares (features/places e LugaresView do iOS): os restaurantes e passeios da família, com nota,
 * comentário, quanto gastaram, marcadores e foto. A foto vai em base64 no próprio lugar e volta
 * como fotoAssetId do piggu-media. Sem mapa embutido por enquanto: "Abrir no mapa" chama o app de
 * mapas do aparelho.
 */
@HiltViewModel
class LugaresViewModel internal constructor(
    private val estiloDeVida: ServicoDeEstiloDeVida,
    private val cache: CacheDeRespostas,
    sessao: Sessao,
    private val relogio: Clock,
    conectado: StateFlow<Boolean>,
) : ViewModel() {
    @Inject constructor(
        estiloDeVida: ServicoDeEstiloDeVida,
        cache: CacheDeRespostas,
        sessao: Sessao,
        relogio: Clock,
        rede: MonitorDeRede,
    ) : this(estiloDeVida, cache, sessao, relogio, rede.conectado)

    private val recarga = MutableStateFlow(0)
    private val estadoAtual = MutableStateFlow(EstadoDosLugares(moeda = sessao.usuario?.preferencias?.moeda ?: "EUR"))
    val estado: StateFlow<EstadoDosLugares> = estadoAtual.asStateFlow()

    init {
        viewModelScope.launch {
            combine(recarga, conectado) { _, conectado -> conectado }.collectLatest { conectado ->
                estadoAtual.update { it.copy(conectado = conectado) }
                cache.carregar("lugares", DadosDosLugares.serializer()) { buscar() }.collect { carga ->
                    val guardadaEm = (carga as? Carga.Pronto)?.guardadaEm
                    estadoAtual.update { it.copy(carga = carga, guardadoEm = guardadaEm?.let(::quandoGuardado)) }
                }
            }
        }
    }

    fun tentarDeNovo() = recarga.update { it + 1 }

    fun abrirNovo() = estadoAtual.update {
        it.copy(formulario = FormularioDeLugar(aberto = true, data = LocalDate.now(relogio).toString()), erro = null)
    }

    fun editar(lugar: Lugar) = estadoAtual.update {
        it.copy(
            formulario = FormularioDeLugar(
                aberto = true,
                editando = lugar,
                nome = lugar.nome,
                categoria = lugar.categoria.orEmpty(),
                localizacao = lugar.localizacao.orEmpty(),
                data = lugar.data,
                valor = lugar.valor.takeIf { v -> v.signum() > 0 },
                nota = lugar.nota,
                comentario = lugar.comentario.orEmpty(),
                marcacoes = lugar.marcacoes,
            ),
            erro = null,
        )
    }

    fun fechar() = mudarFormulario { FormularioDeLugar() }

    // Os limites do PlaceRequest (lifestyle.yaml) e do maxlength do site.
    fun mudarNome(nome: String) = mudarFormulario { it.copy(nome = nome.take(120)) }
    fun mudarCategoria(categoria: String) = mudarFormulario { it.copy(categoria = categoria.take(50)) }
    fun mudarLocalizacao(localizacao: String) = mudarFormulario { it.copy(localizacao = localizacao.take(200)) }
    fun mudarData(data: String) = mudarFormulario { it.copy(data = data) }
    fun mudarValor(valor: BigDecimal?) = mudarFormulario { it.copy(valor = valor) }
    fun mudarNota(nota: Int) = mudarFormulario { it.copy(nota = nota.coerceIn(1, 5)) }
    fun mudarComentario(comentario: String) = mudarFormulario { it.copy(comentario = comentario.take(500)) }
    fun mudarNovoMarcador(nome: String) = mudarFormulario { it.copy(novoMarcador = nome.take(40)) }

    fun alternarMarcador(marcador: String) = mudarFormulario {
        it.copy(marcacoes = if (marcador in it.marcacoes) it.marcacoes - marcador else it.marcacoes + marcador)
    }

    /** A foto da galeria ou da câmera: [abrir] dá um fluxo novo dela a cada chamada. */
    fun escolherFoto(abrir: () -> InputStream?) {
        mudarFormulario { it.copy(lendoFoto = true) }
        viewModelScope.launch {
            val jpeg = withContext(Dispatchers.IO) {
                try {
                    reduzirFoto(abrir)
                } catch (_: IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            }
            mudarFormulario {
                if (jpeg == null) {
                    it.copy(lendoFoto = false, faltou = null, erro = MensagemDeErro.DoApp(R.string.lugares_foto_ilegivel))
                } else {
                    it.copy(lendoFoto = false, foto = Base64.getEncoder().encodeToString(jpeg), erro = null)
                }
            }
        }
    }

    fun criarMarcador() {
        val nome = estadoAtual.value.formulario.novoMarcador.trim()
        if (nome.length < 2) {
            mudarFormulario { it.copy(faltou = CampoDoLugar.Marcador, erro = MensagemDeErro.DoApp(R.string.lugares_marcador_invalido)) }
            return
        }
        viewModelScope.launch {
            try {
                val marcadores = estiloDeVida.criarMarcador(PedidoDeNome(nome))
                mudarDados { it.copy(marcadores = marcadores) }
                mudarFormulario { it.copy(novoMarcador = "", faltou = null, erro = null) }
            } catch (falha: ErroDaApi) {
                mudarFormulario { it.copy(faltou = null, erro = mensagemDeErro(falha)) }
            }
        }
    }

    fun salvar() {
        val f = estadoAtual.value.formulario
        if (f.salvando || f.lendoFoto) return
        val nome = f.nome.trim()
        if (nome.isEmpty()) {
            mudarFormulario { it.copy(faltou = CampoDoLugar.Nome, erro = MensagemDeErro.DoApp(R.string.lugares_falta_nome)) }
            return
        }
        val pedido = NovoLugar(
            nome = nome,
            categoria = f.categoria.trim().ifEmpty { null },
            localizacao = f.localizacao.trim().ifEmpty { null },
            nota = f.nota,
            comentario = f.comentario.trim().ifEmpty { null },
            data = f.data,
            marcacoes = f.marcacoes,
            valor = f.valor ?: BigDecimal.ZERO,
            imageBase64 = f.foto,
            mimeType = f.foto?.let { "image/jpeg" },
        )
        mudarFormulario { it.copy(salvando = true, faltou = null, erro = null) }
        val id = f.editando?.id
        viewModelScope.launch {
            try {
                val salvo = if (id == null) estiloDeVida.criarLugar(pedido) else estiloDeVida.atualizarLugar(id, pedido)
                mudarDados { d ->
                    d.copy(lugares = if (id == null) listOf(salvo) + d.lugares else d.lugares.map { if (it.id == id) salvo else it })
                }
                mudarFormulario { FormularioDeLugar() }
            } catch (falha: ErroDaApi) {
                mudarFormulario { it.copy(salvando = false, erro = mensagemDeErro(falha)) }
            }
        }
    }

    fun pedirParaApagar(lugar: Lugar) = estadoAtual.update { it.copy(apagando = lugar) }
    fun desistir() = estadoAtual.update { it.copy(apagando = null) }

    fun confirmar() {
        val lugar = estadoAtual.value.apagando ?: return
        estadoAtual.update { it.copy(apagando = null) }
        viewModelScope.launch {
            try {
                estiloDeVida.excluirLugar(lugar.id)
                mudarDados { d -> d.copy(lugares = d.lugares.filter { it.id != lugar.id }) }
                estadoAtual.update { it.copy(erro = null) }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(falha)) }
            }
        }
    }

    /** Nenhum app do aparelho abriu o `geo:`. */
    fun semAppDeMapa() = estadoAtual.update { it.copy(erro = MensagemDeErro.DoApp(R.string.lugares_sem_mapa)) }

    private suspend fun buscar(): DadosDosLugares = coroutineScope {
        val lugares = async { estiloDeVida.listarLugares() }
        val marcadores = async { estiloDeVida.listarMarcadores() }
        DadosDosLugares(lugares.await(), marcadores.await())
    }

    private fun mudarFormulario(mudar: (FormularioDeLugar) -> FormularioDeLugar) =
        estadoAtual.update { it.copy(formulario = mudar(it.formulario)) }

    private fun mudarDados(mudar: (DadosDosLugares) -> DadosDosLugares) = estadoAtual.update { e ->
        val pronto = e.carga as? Carga.Pronto ?: return@update e
        e.copy(carga = pronto.copy(valor = mudar(pronto.valor)))
    }

    /** "28 de out., 10:00": quando o guardado foi gravado. */
    private fun quandoGuardado(milissegundos: Long): String =
        DateTimeFormatter.ofPattern("d 'de' MMM, HH:mm", Locale.forLanguageTag("pt-BR"))
            .format(Instant.ofEpochMilli(milissegundos).atZone(relogio.zone))
}
