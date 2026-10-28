package br.com.piggu.feature.plano

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** As duas assinaturas do Play Console, cada uma com um plano básico. */
const val PREMIUM_MENSAL = "premium_mensal"
const val PREMIUM_ANUAL = "premium_anual"

/** Uma assinatura ativa nesta conta do Google; [reconhecida] quando o backend já a recebeu. */
data class CompraDoGooglePlay(val produto: String, val token: String, val reconhecida: Boolean = false)

/** O que a folha de compra do Google Play devolveu. Cancelado não é erro: a tela não diz nada. */
sealed interface ResultadoDaCompra {
    data class Comprada(val compra: CompraDoGooglePlay) : ResultadoDaCompra
    data object Pendente : ResultadoDaCompra
    data object Cancelada : ResultadoDaCompra
    data object Falhou : ResultadoDaCompra
}

/**
 * O Google Play Billing (D-132). O app só compra e lista: o backend confere a compra com o Google
 * e faz o acknowledge, então uma compra que não chegou a ele volta em [compras] na próxima vez.
 * A variante demo e os testes usam a [LojaDeMentira].
 */
interface LojaDoGooglePlay {
    /** O preço de cada produto na loja, já formatado ("R$ 22,89"); vazio sem o Google Play. */
    suspend fun precos(): Map<String, String>

    /** @param conta o id da conta Piggu, que vai em `obfuscatedAccountId` e o backend confere. */
    suspend fun comprar(atividade: Activity, produto: String, conta: String): ResultadoDaCompra

    /** As assinaturas pagas desta conta do Google; nulo quando o Google Play não respondeu. */
    suspend fun compras(): List<CompraDoGooglePlay>?
}

/** A do aparelho: um BillingClient só, ligado na primeira vez que a tela pede. */
@Singleton
class LojaDoGooglePlayDeVerdade @Inject constructor(@ApplicationContext contexto: Context) : LojaDoGooglePlay {
    /** A compra que a tela espera. A que chega depois (pendente aprovada) volta em [compras] ao abrir. */
    @Volatile private var esperando: CompletableDeferred<ResultadoDaCompra>? = null
    private val ligando = Mutex()
    private var detalhes: Map<String, ProductDetails> = emptyMap()

    private val cliente = BillingClient.newBuilder(contexto)
        .setListener { resultado, compras -> esperando?.complete(ler(resultado, compras)) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private suspend fun ligar(): Boolean = ligando.withLock {
        cliente.isReady || suspendCancellableCoroutine { pedido ->
            cliente.startConnection(object : BillingClientStateListener {
                // Chamado de novo a cada religada: só a primeira responde.
                override fun onBillingSetupFinished(resultado: BillingResult) {
                    if (pedido.isActive) pedido.resume(resultado.responseCode == BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() = Unit
            })
        }
    }

    private suspend fun detalhes(): Map<String, ProductDetails> {
        if (detalhes.isNotEmpty() || !ligar()) return detalhes
        val produtos = listOf(PREMIUM_MENSAL, PREMIUM_ANUAL).map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(ProductType.SUBS).build()
        }
        val resposta = cliente.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(produtos).build())
        detalhes = resposta.productDetailsList.orEmpty().associateBy { it.productId }
        return detalhes
    }

    override suspend fun precos(): Map<String, String> =
        detalhes().mapNotNull { (produto, detalhe) ->
            // O preço que se repete, depois do teste grátis se houver.
            oferta(detalhe)?.pricingPhases?.pricingPhaseList?.lastOrNull()?.let { produto to it.formattedPrice }
        }.toMap()

    override suspend fun comprar(atividade: Activity, produto: String, conta: String): ResultadoDaCompra {
        val detalhe = detalhes()[produto] ?: return ResultadoDaCompra.Falhou
        val oferta = oferta(detalhe) ?: return ResultadoDaCompra.Falhou
        val parametros = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(detalhe).setOfferToken(oferta.offerToken).build()),
            )
            .setObfuscatedAccountId(conta)
            .build()
        val espera = CompletableDeferred<ResultadoDaCompra>().also { esperando = it }
        val aberta = withContext(Dispatchers.Main) { cliente.launchBillingFlow(atividade, parametros) }
        return when (aberta.responseCode) {
            BillingResponseCode.OK -> espera.await()
            BillingResponseCode.USER_CANCELED -> ResultadoDaCompra.Cancelada
            // Já assinada nesta conta do Google: manda a que existe ao backend.
            BillingResponseCode.ITEM_ALREADY_OWNED ->
                compras()?.firstOrNull { it.produto == produto }?.let(ResultadoDaCompra::Comprada) ?: ResultadoDaCompra.Falhou
            else -> ResultadoDaCompra.Falhou
        }
    }

    override suspend fun compras(): List<CompraDoGooglePlay>? {
        if (!ligar()) return null
        val resposta = cliente.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build())
        if (resposta.billingResult.responseCode != BillingResponseCode.OK) return null
        return resposta.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.map { it.paraCompra() }
    }

    private fun ler(resultado: BillingResult, compras: List<Purchase>?): ResultadoDaCompra = when (resultado.responseCode) {
        BillingResponseCode.OK -> compras?.firstOrNull()?.let {
            if (it.purchaseState == Purchase.PurchaseState.PURCHASED) ResultadoDaCompra.Comprada(it.paraCompra()) else ResultadoDaCompra.Pendente
        } ?: ResultadoDaCompra.Falhou
        BillingResponseCode.USER_CANCELED -> ResultadoDaCompra.Cancelada
        else -> ResultadoDaCompra.Falhou
    }

    private fun Purchase.paraCompra() = CompraDoGooglePlay(products.first(), purchaseToken, isAcknowledged)

    /** A oferta de teste grátis, quando a pessoa ainda tem direito a ela; senão a do plano básico. */
    private fun oferta(detalhe: ProductDetails): ProductDetails.SubscriptionOfferDetails? {
        val ofertas = detalhe.subscriptionOfferDetails.orEmpty()
        return ofertas.firstOrNull { oferta -> oferta.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } }
            ?: ofertas.firstOrNull { it.offerId == null }
    }
}

/**
 * A loja da variante demo e dos testes: "compra" na hora com um token falso, que o app manda à
 * API falsa como mandaria a de verdade ao backend.
 */
class LojaDeMentira(
    private val precos: Map<String, String> = emptyMap(),
    private val resultado: (String) -> ResultadoDaCompra = { ResultadoDaCompra.Comprada(CompraDoGooglePlay(it, "token-de-mentira-$it")) },
) : LojaDoGooglePlay {
    /** As assinaturas desta "conta do Google"; os testes põem aqui a compra que não chegou ao backend. */
    val compradas = mutableListOf<CompraDoGooglePlay>()

    override suspend fun precos() = precos

    override suspend fun comprar(atividade: Activity, produto: String, conta: String): ResultadoDaCompra =
        resultado(produto).also { if (it is ResultadoDaCompra.Comprada) compradas += it.compra }

    override suspend fun compras(): List<CompraDoGooglePlay> = compradas.toList()
}
