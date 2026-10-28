package br.com.piggu.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** O que é deste aparelho, e não da conta: o modo claro ou escuro e as cores do papel de parede. */
class PreferenciasDoAparelho(private val dados: DataStore<Preferences>) {

    /** "automatico", "claro" ou "escuro", como o ModoDeAparencia do site; nulo é automático. */
    val modo: Flow<String?> = dados.data.map { it[MODO] }

    /** Cores do celular (Material You), no Android 12 ou mais novo. */
    val coresDoCelular: Flow<Boolean> = dados.data.map { it[CORES_DO_CELULAR] ?: false }

    suspend fun mudarModo(modo: String) {
        dados.edit { it[MODO] = modo }
    }

    suspend fun mudarCoresDoCelular(ligadas: Boolean) {
        dados.edit { it[CORES_DO_CELULAR] = ligadas }
    }

    private companion object {
        val MODO = stringPreferencesKey("modo")
        val CORES_DO_CELULAR = booleanPreferencesKey("cores_do_celular")
    }
}
