package br.com.piggu.app

import br.com.piggu.feature.plano.LojaDoGooglePlay
import br.com.piggu.feature.plano.LojaDoGooglePlayDeVerdade
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Variante producao: a assinatura pelo Google Play de verdade (D-132). */
@Module
@InstallIn(SingletonComponent::class)
interface ModuloDaVariante {
    @Binds
    fun loja(loja: LojaDoGooglePlayDeVerdade): LojaDoGooglePlay
}
