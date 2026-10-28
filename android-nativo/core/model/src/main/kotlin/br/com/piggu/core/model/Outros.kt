package br.com.piggu.core.model

import kotlinx.serialization.Serializable

// Tipos do banking, lifestyle, media e rewards (docs/api/*.yaml). Regra de nulos em Conta.kt.

// Bancos (Open Finance, piggu-banking).

/** BankAccountResponse: conta de um banco conectado, com o saldo da última sincronização. */
@Serializable
data class ContaBancaria(
    val id: String,
    /** O banco conectado ao qual a conta pertence; é ele que se desconecta. */
    val conexaoId: String,
    val instituicao: String,
    val nome: String,
    val tipo: String,
    val numero: String? = null,
    val saldo: Decimal,
    val moeda: String,
    val status: String,
    val atualizadoEm: String? = null,
)

/** ConnectTokenResponse: token de 30 minutos que abre o widget da Pluggy. */
@Serializable
data class ConnectToken(val accessToken: String, val sandbox: Boolean)

/** ConsentimentoRequest: leva a autorização da pessoa, que o backend grava no mesmo pedido. */
@Serializable
data class PedidoDeConexao(val versaoDoAviso: String, val autorizo: Boolean = true)

/** RegisterItemRequest: o item que o widget devolveu. */
@Serializable
data class PedidoDeItem(val itemId: String)

/** Débito lido de um banco conectado, antes de virar gasto. */
@Serializable
data class GastoDoBanco(
    val data: String,
    val descricao: String,
    val valor: Decimal,
    val idExterno: String,
    /** Banco e conta de onde saiu, para conferir na revisão. */
    val conta: String,
    val moeda: String,
)

/** Open Finance é opcional: desligado esconde o card. */
@Serializable
data class SituacaoDoOpenFinance(val habilitado: Boolean)

// Estilo de vida (piggu-lifestyle).

/** A lista do item de compra; no JSON "Compras" e "Desejos". */
@Serializable(with = ListaDeCompras.Serializador::class)
enum class ListaDeCompras {
    COMPRAS, DESEJOS, DESCONHECIDO;

    object Serializador : SerializadorDeEnum<ListaDeCompras>(
        entries,
        DESCONHECIDO,
        { it.name.lowercase().replaceFirstChar(Char::uppercaseChar) },
    )
}

/** PlaceResponse. */
@Serializable
data class Lugar(
    val id: String,
    val nome: String,
    val categoria: String? = null,
    val localizacao: String? = null,
    val nota: Int,
    val comentario: String? = null,
    val data: String,
    val marcacoes: List<String> = emptyList(),
    val fotoAssetId: String? = null,
    val temFoto: Boolean? = null,
    val valor: Decimal,
    val usuario: String? = null,
)

/** PlaceRequest. */
@Serializable
data class NovoLugar(
    val nome: String,
    val categoria: String? = null,
    val localizacao: String? = null,
    val nota: Int,
    val comentario: String? = null,
    val data: String,
    val marcacoes: List<String>? = null,
    val valor: Decimal? = null,
    val imageBase64: String? = null,
    val mimeType: String? = null,
)

/** PersonalGoalResponse: meta pessoal do mês; só a pessoa vê as próprias. */
@Serializable
data class MetaDoMes(
    val id: String,
    val titulo: String,
    val alvo: Int,
    val atual: Int,
    /** AAAA-MM */
    val mes: String? = null,
    val concluida: Boolean,
)

/** PersonalGoalRequest. */
@Serializable
data class NovaMetaDoMes(val titulo: String, val alvo: Int, val atual: Int)

/** GirlsMathResponse: algo que a pessoa deixou de comprar; só ela vê. */
@Serializable
data class ItemDoGirlsMath(
    val id: String,
    val produto: String,
    val loja: String? = null,
    val valor: Decimal,
    val data: String,
    val fotoAssetId: String? = null,
    val temFoto: Boolean? = null,
)

/** GirlsMathRequest. */
@Serializable
data class NovoItemDoGirlsMath(
    val produto: String,
    val loja: String? = null,
    val valor: Decimal,
    val data: String? = null,
    val imageBase64: String? = null,
    val mimeType: String? = null,
)

/** MovieResponse: filme da lista do casal. */
@Serializable
data class Filme(
    val id: String,
    val tmdbId: String,
    val titulo: String,
    val ano: String? = null,
    val poster: String? = null,
    val nota: Decimal? = null,
    val sinopse: String? = null,
    val assistido: Boolean,
    /** Id da pessoa para a nota dela, de 1 a 5. */
    val avaliacoes: Map<String, Int> = emptyMap(),
    val usuario: String? = null,
)

/** TmdbMovie: filme do TMDB, ainda fora da lista (também o corpo de POST `/movies`). */
@Serializable
data class FilmeDoCatalogo(
    val tmdbId: String,
    val titulo: String,
    val ano: String? = null,
    val poster: String? = null,
    val nota: Decimal? = null,
    val sinopse: String? = null,
)

/** MovieRatingRequest. */
@Serializable
data class PedidoDeAvaliacao(val nota: Int)

/** ShoppingItemResponse. */
@Serializable
data class ItemDeCompra(
    val id: String,
    val item: String,
    val quantidade: String? = null,
    val lista: ListaDeCompras,
    val comprado: Boolean,
    val marca: String? = null,
    /** Só HTTPS: o backend descarta outro esquema. */
    val imagem: String? = null,
    val codigo: String? = null,
    val usuario: String? = null,
)

/** ShoppingItemRequest. */
@Serializable
data class NovoItemDeCompra(
    val item: String,
    val quantidade: String? = null,
    val lista: ListaDeCompras? = null,
    val marca: String? = null,
    val imagem: String? = null,
    val codigo: String? = null,
)

/** CatalogProduct: produto do catálogo aberto, para preencher marca, imagem e código. */
@Serializable
data class ProdutoDoCatalogo(
    val codigo: String,
    val nome: String,
    val marca: String? = null,
    val quantidade: String? = null,
    val imagem: String? = null,
)

// Fotos e arquivos (piggu-media).

/** FeedPhotoResponse. */
@Serializable
data class FotoDoFeed(
    val id: String,
    val mesKey: String,
    val assetId: String,
    val legenda: String? = null,
    val usuario: String? = null,
    val criadoEm: String,
)

/** FeedPhotoRequest. */
@Serializable
data class NovaFotoDoFeed(val mesKey: String, val imageBase64: String, val mimeType: String)

/** CaptionRequest. */
@Serializable
data class PedidoDeLegenda(val legenda: String)

/** AssetResponse. */
@Serializable
data class Arquivo(
    val id: String,
    val contentType: String,
    val tamanho: Long,
    val contexto: String,
    val criadoEm: String,
)

// Fofocoins, prêmios e resgates (piggu-rewards).

/** CoinBalanceResponse. */
@Serializable
data class SaldoDeMoedas(val saldo: Int, val historico: List<MovimentoDeMoedas> = emptyList())

/** Movimento. */
@Serializable
data class MovimentoDeMoedas(
    val id: String,
    val data: String,
    val valor: Int,
    val motivo: String,
    /** Dono das moedas; hoje sempre "USUARIA", como na planilha. */
    val usuario: String,
)

/** CoinAdjustRequest: positivo credita, negativo debita. Só o administrador. */
@Serializable
data class PedidoDeAjuste(val valor: Int, val motivo: String)

/** PrizeResponse. */
@Serializable
data class Premio(
    val id: String,
    val nome: String,
    val descricao: String? = null,
    val preco: Int,
    val ativo: Boolean,
)

/** PrizeRequest. */
@Serializable
data class NovoPremio(
    val nome: String,
    val descricao: String? = null,
    val preco: Int,
    val ativo: Boolean? = null,
)

/** RedemptionResponse. */
@Serializable
data class Resgate(
    val id: String,
    val premio: String,
    val preco: Int,
    val usuario: String? = null,
    val status: String,
    val data: String,
    val saldo: Int,
)
