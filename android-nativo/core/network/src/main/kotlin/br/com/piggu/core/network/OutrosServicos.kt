package br.com.piggu.core.network

import br.com.piggu.core.model.ConnectToken
import br.com.piggu.core.model.ContaBancaria
import br.com.piggu.core.model.EventoDeAuditoria
import br.com.piggu.core.model.Filme
import br.com.piggu.core.model.FilmeDoCatalogo
import br.com.piggu.core.model.FotoDoFeed
import br.com.piggu.core.model.GastoDoBanco
import br.com.piggu.core.model.ItemDeCompra
import br.com.piggu.core.model.ItemDoGirlsMath
import br.com.piggu.core.model.Lugar
import br.com.piggu.core.model.MetaDoMes
import br.com.piggu.core.model.NovaFotoDoFeed
import br.com.piggu.core.model.NovaMetaDoMes
import br.com.piggu.core.model.NovoItemDeCompra
import br.com.piggu.core.model.NovoItemDoGirlsMath
import br.com.piggu.core.model.NovoLugar
import br.com.piggu.core.model.NovoPremio
import br.com.piggu.core.model.PedidoDeAjuste
import br.com.piggu.core.model.PedidoDeAvaliacao
import br.com.piggu.core.model.PedidoDeConexao
import br.com.piggu.core.model.PedidoDeItem
import br.com.piggu.core.model.PedidoDeLegenda
import br.com.piggu.core.model.PedidoDeNome
import br.com.piggu.core.model.Premio
import br.com.piggu.core.model.ProdutoDoCatalogo
import br.com.piggu.core.model.Resgate
import br.com.piggu.core.model.SaldoDeMoedas
import br.com.piggu.core.model.SituacaoDoOpenFinance
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** piggu-banking (banking.service.ts): bancos conectados por Open Finance (Pluggy). */
interface ServicoDeBancos {
    /** Desligado (ou serviço fora do ar) esconde o card. */
    @GET("banking/status")
    suspend fun status(): SituacaoDoOpenFinance

    /** Token de 30 minutos que abre o widget da Pluggy, levando a autorização da pessoa. */
    @POST("banking/connect-token")
    suspend fun gerarConnectToken(@Body pedido: PedidoDeConexao): ConnectToken

    /** Registra o item que o widget devolveu e já traz as contas dele. */
    @POST("banking/items")
    suspend fun registrarItem(@Body pedido: PedidoDeItem): List<ContaBancaria>

    @GET("banking/accounts")
    suspend fun listarContas(): List<ContaBancaria>

    /** Apaga a conexão na Pluggy e as contas dela. Sempre grátis. */
    @DELETE("banking/connections/{id}")
    suspend fun desconectar(@Path("id") conexaoId: String): List<ContaBancaria>

    /** Débitos confirmados desde a data (até 90 dias). Premium. */
    @GET("banking/transactions")
    suspend fun gastosDoBanco(@Query("desde") desde: String): List<GastoDoBanco>

    @POST("banking/sync")
    suspend fun sincronizar(@Body vazio: JsonObject = JsonObject(emptyMap())): List<ContaBancaria>
}

/** piggu-lifestyle (lifestyle.service.ts): lugares, filmes, compras, metas do mês e Girls Math. */
interface ServicoDeEstiloDeVida {
    @GET("places")
    suspend fun listarLugares(): List<Lugar>

    @POST("places")
    suspend fun criarLugar(@Body lugar: NovoLugar): Lugar

    @PUT("places/{id}")
    suspend fun atualizarLugar(@Path("id") id: String, @Body lugar: NovoLugar): Lugar

    @DELETE("places/{id}")
    suspend fun excluirLugar(@Path("id") id: String)

    @GET("places/tags")
    suspend fun listarMarcadores(): List<String>

    @POST("places/tags")
    suspend fun criarMarcador(@Body pedido: PedidoDeNome): List<String>

    @GET("movies")
    suspend fun listarFilmes(): List<Filme>

    @GET("movies/search")
    suspend fun buscarFilmes(@Query("busca") busca: String): List<FilmeDoCatalogo>

    /** @param genero id de gênero do TMDB; nulo sorteia entre todos */
    @GET("movies/random")
    suspend fun sortearFilme(@Query("genero") genero: String? = null): FilmeDoCatalogo

    @POST("movies")
    suspend fun adicionarFilme(@Body filme: FilmeDoCatalogo): Filme

    @PATCH("movies/{id}/watched")
    suspend fun marcarFilmeAssistido(@Path("id") id: String, @Query("assistido") assistido: Boolean): Filme

    @PUT("movies/{id}/rating")
    suspend fun avaliarFilme(@Path("id") id: String, @Body pedido: PedidoDeAvaliacao): Filme

    @DELETE("movies/{id}")
    suspend fun excluirFilme(@Path("id") id: String)

    /** @param lista "Compras" ou "Desejos"; nula traz as duas */
    @GET("shopping/items")
    suspend fun listarCompras(@Query("lista") lista: String? = null): List<ItemDeCompra>

    @POST("shopping/items")
    suspend fun criarItemDeCompra(@Body item: NovoItemDeCompra): ItemDeCompra

    @PATCH("shopping/items/{id}/purchased")
    suspend fun marcarComprado(@Path("id") id: String, @Query("comprado") comprado: Boolean): ItemDeCompra

    @DELETE("shopping/items/{id}")
    suspend fun excluirItemDeCompra(@Path("id") id: String)

    /** Busca no catálogo aberto, para preencher marca, imagem e código de barras. */
    @GET("shopping/catalog")
    suspend fun buscarNoCatalogo(@Query("busca") busca: String): List<ProdutoDoCatalogo>

    /** @param mes AAAA-MM; nulo é o mês atual */
    @GET("personal-goals")
    suspend fun listarMetasDoMes(@Query("mes") mes: String? = null): List<MetaDoMes>

    @POST("personal-goals")
    suspend fun criarMetaDoMes(@Body meta: NovaMetaDoMes): MetaDoMes

    @PUT("personal-goals/{id}")
    suspend fun atualizarMetaDoMes(@Path("id") id: String, @Body meta: NovaMetaDoMes): MetaDoMes

    @DELETE("personal-goals/{id}")
    suspend fun excluirMetaDoMes(@Path("id") id: String)

    @GET("girls-math")
    suspend fun listarGirlsMath(): List<ItemDoGirlsMath>

    @POST("girls-math")
    suspend fun criarNoGirlsMath(@Body item: NovoItemDoGirlsMath): ItemDoGirlsMath

    @DELETE("girls-math/{id}")
    suspend fun excluirDoGirlsMath(@Path("id") id: String)
}

/** piggu-media (media.service.ts): feed de fotos e arquivos de imagem. */
interface ServicoDeMidia {
    /** @param mes filtro opcional, AAAA-MM */
    @GET("feed")
    suspend fun listarFeed(@Query("mes") mes: String? = null): List<FotoDoFeed>

    @POST("feed")
    suspend fun publicarNoFeed(@Body foto: NovaFotoDoFeed): FotoDoFeed

    @PATCH("feed/{id}/caption")
    suspend fun legendar(@Path("id") id: String, @Body pedido: PedidoDeLegenda): FotoDoFeed

    @DELETE("feed/{id}")
    suspend fun excluirDoFeed(@Path("id") id: String)

    @Streaming
    @GET("assets/{id}/content")
    suspend fun baixarArquivo(@Path("id") assetId: String): ResponseBody

    @DELETE("assets/{id}")
    suspend fun excluirArquivo(@Path("id") assetId: String)
}

/** Endereço dos bytes da imagem, com cache de 30 dias: serve direto ao carregador de imagem. */
fun urlDaImagem(urlDaApi: String, assetId: String): String = "${urlDaApi.trimEnd('/')}/assets/$assetId/content"

/** piggu-rewards (rewards.service.ts): Fofocoins, prêmios e resgates. */
interface ServicoDeRecompensas {
    @GET("coins")
    suspend fun consultarSaldo(): SaldoDeMoedas

    /** Credita valor positivo, debita negativo. Só o administrador. */
    @POST("coins/adjustments")
    suspend fun ajustarMoedas(@Body pedido: PedidoDeAjuste): SaldoDeMoedas

    /** @param todos inclui os prêmios desativados, para a tela de gestão */
    @GET("prizes")
    suspend fun listarPremios(@Query("todos") todos: Boolean = false): List<Premio>

    @POST("prizes")
    suspend fun criarPremio(@Body premio: NovoPremio): Premio

    @PUT("prizes/{id}")
    suspend fun atualizarPremio(@Path("id") id: String, @Body premio: NovoPremio): Premio

    @DELETE("prizes/{id}")
    suspend fun excluirPremio(@Path("id") id: String)

    @POST("prizes/{id}/redemptions")
    suspend fun resgatar(@Path("id") premioId: String, @Body vazio: JsonObject = JsonObject(emptyMap())): Resgate

    @GET("prizes/redemptions")
    suspend fun listarResgates(): List<Resgate>
}

/**
 * O histórico da família, como o history.service.ts: junta o do dinheiro (finance) e o das
 * pessoas (identity), do mais novo ao mais velho. Um serviço fora do ar não esconde o outro.
 */
suspend fun historicoDaFamilia(
    identidade: ServicoDeIdentidade,
    financas: ServicoDeFinancas,
    limite: Int = 50,
): List<EventoDeAuditoria> = coroutineScope {
    val dinheiro = async { ouVazio { financas.historicoDoDinheiro(limite) } }
    val pessoas = async { ouVazio { identidade.historicoDasPessoas(limite) } }
    (dinheiro.await() + pessoas.await()).sortedByDescending { it.quando }.take(limite)
}

private suspend fun ouVazio(pedir: suspend () -> List<EventoDeAuditoria>): List<EventoDeAuditoria> =
    try {
        pedir()
    } catch (_: ErroDaApi) {
        emptyList()
    }
