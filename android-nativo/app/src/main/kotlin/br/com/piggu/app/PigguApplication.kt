package br.com.piggu.app

import android.app.Application
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
class PigguApplication : Application(), Configuration.Provider {
    @Inject lateinit var fabricaDeWorkers: HiltWorkerFactory
    @Inject lateinit var envioDaFila: EnvioDaFila
    @Inject lateinit var iconesDoApp: IconesDoApp

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(fabricaDeWorkers).build()

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
