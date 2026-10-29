package br.com.piggu.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.sessao: DataStore<Preferences> by preferencesDataStore("sessao")
private val Context.aparelho: DataStore<Preferences> by preferencesDataStore("aparelho")

@Module
@InstallIn(SingletonComponent::class)
object ModuloDoDatastore {
    @Provides
    @Singleton
    fun cofreDaSessao(@ApplicationContext contexto: Context): CofreDaSessao =
        CofreDaSessao(contexto.sessao, CifraDoKeystore())

    @Provides
    @Singleton
    fun preferenciasDoAparelho(@ApplicationContext contexto: Context): PreferenciasDoAparelho =
        PreferenciasDoAparelho(contexto.aparelho)
}
