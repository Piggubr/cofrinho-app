package br.com.piggu.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Singleton

private val Context.painel: DataStore<Preferences> by preferencesDataStore("painel")

/**
 * O Painel deste aparelho, por pessoa, como no localStorage do site: a ordem dos cartões
 * (`piggu_painel_<id>`, painel.ts) e o guia de primeiros passos (`piggu_guia_<id>`,
 * primeiros-passos.ts). Guarda o JSON como o site grava; quem lê e escreve é o Painel.
 */
class PainelDoAparelho(private val dados: DataStore<Preferences>) {

    fun ordem(usuario: String): Flow<String?> = dados.data.map { it[chaveDaOrdem(usuario)] }

    suspend fun guardarOrdem(usuario: String, json: String) {
        dados.edit { it[chaveDaOrdem(usuario)] = json }
    }

    fun guia(usuario: String): Flow<String?> = dados.data.map { it[chaveDoGuia(usuario)] }

    suspend fun guardarGuia(usuario: String, json: String) {
        dados.edit { it[chaveDoGuia(usuario)] = json }
    }

    private companion object {
        fun chaveDaOrdem(usuario: String) = stringPreferencesKey("piggu_painel_$usuario")
        fun chaveDoGuia(usuario: String) = stringPreferencesKey("piggu_guia_$usuario")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ModuloDoPainel {
    @Provides
    @Singleton
    fun painelDoAparelho(@ApplicationContext contexto: Context): PainelDoAparelho = PainelDoAparelho(contexto.painel)
}
