package br.com.piggu.app

import br.com.piggu.core.network.RespostasDaDemo
import br.com.piggu.core.testing.ApiDaDemo
import br.com.piggu.feature.plano.LojaDeMentira
import br.com.piggu.feature.plano.LojaDoGooglePlay
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.Interceptor
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/** Variante demo: a API falsa (api-da-demo.ts) responde tudo, com o atraso de uma rede de verdade, e a loja do Google Play é de mentira. */
@Module
@InstallIn(SingletonComponent::class)
object ModuloDaVariante {
    @Provides
    @Singleton
    @IntoSet
    @RespostasDaDemo
    fun apiDaDemo(): Interceptor = ApiDaDemo(atraso = 250.milliseconds)

    /** A loja de mentira "compra" na hora e manda o token falso à API falsa. */
    @Provides
    @Singleton
    fun loja(): LojaDoGooglePlay = LojaDeMentira()
}
