package br.com.piggu.app.moldura

import android.content.Context
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.os.bundleOf
import br.com.piggu.app.BuildConfig
import br.com.piggu.app.R
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** O SDK da AdMob sobe uma vez, fora da thread principal, só quando o primeiro banner aparece. */
private object SdkDeAnuncios {
    private val iniciado = AtomicBoolean(false)

    suspend fun iniciar(contexto: Context) {
        if (!iniciado.compareAndSet(false, true)) return
        withContext(Dispatchers.IO) {
            // Segunda proteção depois da idade (D-180): só anúncio para todos os públicos.
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
                    .build(),
            )
            MobileAds.initialize(contexto) {}
        }
    }
}

/**
 * O banner adaptativo ancorado do plano gratuito (D-180), sempre não personalizado (`npa=1`).
 * Nenhum dado do Piggu vai no pedido. Fica com altura zero até chegar um anúncio: sem internet
 * ou sem anúncio, não sobra espaço vazio. Sem id de banner (release sem ADMOB_BANNER_ID), não
 * desenha nada.
 */
@Composable
fun BannerDeAnuncio(modifier: Modifier = Modifier) {
    if (BuildConfig.ADMOB_BANNER.isEmpty()) return
    val contexto = LocalContext.current
    LaunchedEffect(Unit) { SdkDeAnuncios.iniciar(contexto.applicationContext) }
    val descricao = stringResource(R.string.anuncio)

    // A largura vem do espaço da moldura, não da tela (trilho, gaveta, tablet, paisagem).
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val largura = maxWidth.value.toInt()
        key(largura) {
            var carregou by remember { mutableStateOf(false) }
            AndroidView(
                factory = { ctx ->
                    AdView(ctx).apply {
                        setAdSize(AdSize.getLargeAnchoredAdaptiveBannerAdSize(ctx, largura))
                        adUnitId = BuildConfig.ADMOB_BANNER
                        adListener = object : AdListener() {
                            override fun onAdLoaded() {
                                carregou = true
                            }

                            override fun onAdFailedToLoad(erro: LoadAdError) {
                                carregou = false
                            }
                        }
                        loadAd(
                            AdRequest.Builder()
                                .addNetworkExtrasBundle(AdMobAdapter::class.java, bundleOf("npa" to "1"))
                                .build(),
                        )
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (carregou) Modifier.wrapContentHeight() else Modifier.height(0.dp))
                    .semantics { contentDescription = descricao },
            )
        }
    }
}
