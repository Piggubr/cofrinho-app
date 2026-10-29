package br.com.piggu.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import br.com.piggu.core.data.EnvioDaFila
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** O WorkManager nasce sob demanda com a fábrica do Hilt (o inicializador padrão sai no manifest). */
@HiltAndroidApp
class PigguApplication : Application(), Configuration.Provider {
    @Inject lateinit var fabricaDeWorkers: HiltWorkerFactory
    @Inject lateinit var envioDaFila: EnvioDaFila

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(fabricaDeWorkers).build()

    override fun onCreate() {
        super.onCreate()
        // A fila de gastos sobe ao abrir o app, ao entrar e quando a rede volta.
        envioDaFila.vigiar()
    }
}
