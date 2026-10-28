package br.com.piggu.app

import br.com.piggu.core.data.ModuloDoEscopo
import br.com.piggu.core.network.RespostasDaDemo
import br.com.piggu.core.testing.ApiDaDemo
import br.com.piggu.feature.plano.LojaDeMentira
import br.com.piggu.feature.plano.LojaDoGooglePlay
import br.com.piggu.core.testing.DIA_DAS_CAPTURAS
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Interceptor
import java.time.Clock
import javax.inject.Singleton

/** A API falsa sem o atraso e no dia das capturas, para cada imagem sair igual. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ModuloDaVariante::class])
object DemoDasCapturas {
    @Provides
    @Singleton
    @IntoSet
    @RespostasDaDemo
    fun apiDaDemo(): Interceptor = ApiDaDemo(DIA_DAS_CAPTURAS)

    /** A loja de mentira "compra" na hora e manda o token falso à API falsa. */
    @Provides
    @Singleton
    fun loja(): LojaDoGooglePlay = LojaDeMentira()
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ModuloDoEscopo::class])
object EscopoDasCapturas {
    @Provides
    @Singleton
    @br.com.piggu.core.data.EscopoDoApp
    fun escopo(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun relogio(): Clock = DIA_DAS_CAPTURAS
}
