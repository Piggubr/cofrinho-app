package br.com.piggu.app

import br.com.piggu.core.network.URL_DA_API
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

/** O que a rede pede ao app: a versão para o User-Agent e o endereço da API. */
@Module
@InstallIn(SingletonComponent::class)
object ModuloDoApp {
    @Provides
    @Named("versaoDoApp")
    fun versaoDoApp(): String = BuildConfig.VERSION_NAME

    // Na demo as respostas vêm da API falsa antes de sair do aparelho; o endereço só dá forma às rotas.
    @Provides
    @Named("urlDaApi")
    fun urlDaApi(): String = URL_DA_API
}
