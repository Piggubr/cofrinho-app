package br.com.piggu.app

import android.app.Application
import android.content.Context
import br.com.piggu.core.network.urlDaImagem
import br.com.piggu.core.ui.AssetDoPiggu
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.map.Mapper
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import javax.inject.Named
import okhttp3.Call
import okhttp3.OkHttpClient
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import br.com.piggu.feature.perfil.IconesDoApp
import androidx.work.Configuration
import br.com.piggu.core.data.EnvioDaFila
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** O WorkManager nasce sob demanda com a fábrica do Hilt (o inicializador padrão sai no manifest). */
@HiltAndroidApp
class PigguApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {
    @Inject lateinit var fabricaDeWorkers: HiltWorkerFactory
    @Inject lateinit var cliente: OkHttpClient
    @Inject @Named("urlDaApi") lateinit var urlDaApi: String
    @Inject lateinit var envioDaFila: EnvioDaFila
    @Inject lateinit var iconesDoApp: IconesDoApp

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(fabricaDeWorkers).build()

    /**
     * As imagens do piggu-media vão pelo OkHttp da API (com o token e, na demo, a API falsa); as de
     * fora (catálogo de compras, pôsteres) vão por um cliente sem a sessão: o token nunca sai do Piggu.
     */
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(Mapper<AssetDoPiggu, String> { asset, _ -> urlDaImagem(urlDaApi, asset.id) })
            val semSessao = OkHttpClient.Builder().connectionPool(cliente.connectionPool).build()
            add(OkHttpNetworkFetcherFactory(callFactory = { chamadasDeImagem(urlDaApi, cliente, semSessao) }))
            add(SvgDecoder.Factory())
        }
        .build()

    override fun onCreate() {
        super.onCreate()
        // A fila de gastos sobe ao abrir o app, ao entrar e quando a rede volta.
        envioDaFila.vigiar()
        // O ícone escolhido no Perfil troca quando o app sai da frente: trocar com ele aberto fecha o app.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = iconesDoApp.aplicarPendente()
        })
    }
}

/** Só o que é da API do Piggu vai pelo cliente com a sessão; o resto, pelo [semSessao]. */
internal fun chamadasDeImagem(urlDaApi: String, comSessao: Call.Factory, semSessao: Call.Factory) = Call.Factory { pedido ->
    (if (pedido.url.toString().startsWith(urlDaApi)) comSessao else semSessao).newCall(pedido)
}
