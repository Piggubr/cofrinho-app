package br.com.piggu.core.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Escopo que vive com o processo: renovação da sessão por trás, fila de gastos, monitor de rede. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EscopoDoApp

@Module
@InstallIn(SingletonComponent::class)
object ModuloDoEscopo {
    @Provides
    @Singleton
    @EscopoDoApp
    fun escopo(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
