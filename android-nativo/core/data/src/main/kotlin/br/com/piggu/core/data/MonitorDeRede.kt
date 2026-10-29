package br.com.piggu.core.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Se o aparelho tem internet agora (uma rede com INTERNET e VALIDATED), pelo NetworkCallback.
 * As telas usam para dizer "sem internet" e a fila de gastos para enviar quando a rede volta.
 */
@Singleton
class MonitorDeRede @Inject constructor(
    @ApplicationContext contexto: Context,
    @EscopoDoApp escopo: CoroutineScope,
) {
    private val conectividade = contexto.getSystemService(ConnectivityManager::class.java)

    val conectado: StateFlow<Boolean> = callbackFlow {
        val redes = mutableSetOf<Network>()
        val aviso = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(rede: Network, capacidades: NetworkCapabilities) {
                if (capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) redes += rede else redes -= rede
                trySend(redes.isNotEmpty())
            }

            override fun onLost(rede: Network) {
                redes -= rede
                trySend(redes.isNotEmpty())
            }
        }
        val pedido = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        conectividade.registerNetworkCallback(pedido, aviso)
        trySend(agora())
        awaitClose { conectividade.unregisterNetworkCallback(aviso) }
    }.conflate().stateIn(escopo, SharingStarted.WhileSubscribed(5_000), agora())

    private fun agora(): Boolean = conectividade.getNetworkCapabilities(conectividade.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
}
