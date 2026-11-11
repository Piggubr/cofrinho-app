package br.com.piggu.core.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import java.time.ZoneId
import javax.inject.Qualifier
import javax.inject.Singleton

/** Escopo que vive com o processo: renovação da sessão por trás, fila de gastos, monitor de rede. */
/** O fuso de todas as contas do Piggu: o backend fecha o dia e o mês em Brasília. */
val FUSO_DA_CONTA: ZoneId = ZoneId.of("America/Sao_Paulo")

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

    /**
     * O relógio do app, no fuso da conta (o do backend, Brasília): "hoje" e o "mês atual" não
     * mudam com o fuso do aparelho de quem viaja (BUG-029). Os testes e as capturas fixam o dia.
     */
    @Provides
    fun relogio(): Clock = Clock.system(FUSO_DA_CONTA)
}
