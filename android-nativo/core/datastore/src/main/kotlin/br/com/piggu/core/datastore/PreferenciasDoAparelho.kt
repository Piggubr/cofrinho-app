package br.com.piggu.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * O que é deste aparelho, e não da conta, como no localStorage do site (tema.ts): a cor, o modo
 * claro ou escuro, as cores do papel de parede e a marca de que a cor voltou ao padrão.
 */
class PreferenciasDoAparelho(private val dados: DataStore<Preferences>) {

    /** O id da cor (IdDoTema do site); nulo é a padrão. */
    val cor: Flow<String?> = dados.data.map { it[COR] }

    /** "automatico", "claro" ou "escuro" (ModoDeAparencia do site); nulo é automático. */
    val modo: Flow<String?> = dados.data.map { it[MODO] }

    /** Cores do celular (Material You), no Android 12 ou mais novo. Só existe no Android. */
    val coresDoCelular: Flow<Boolean> = dados.data.map { it[CORES_DO_CELULAR] ?: false }

    /** A cor Premium voltou ao padrão porque o Premium acabou; sai depois que o aviso aparece. */
    val voltouAoPadrao: Flow<Boolean> = dados.data.map { it[VOLTOU] ?: false }

    suspend fun mudarCor(id: String) {
        dados.edit { it[COR] = id }
    }

    suspend fun mudarModo(id: String) {
        dados.edit { it[MODO] = id }
    }

    suspend fun mudarCoresDoCelular(ligadas: Boolean) {
        dados.edit { it[CORES_DO_CELULAR] = ligadas }
    }

    /** marcarQueVoltouAoPadrao do site: grava a padrão e marca o aviso, numa escrita só. */
    suspend fun voltarAoPadrao(idDaPadrao: String) {
        dados.edit {
            it[COR] = idDaPadrao
            it[VOLTOU] = true
        }
    }

    suspend fun esquecerQueVoltouAoPadrao() {
        dados.edit { it.remove(VOLTOU) }
    }

    private companion object {
        // Os mesmos nomes do localStorage do site.
        val COR = stringPreferencesKey("piggu_tema")
        val MODO = stringPreferencesKey("piggu_modo")
        val VOLTOU = booleanPreferencesKey("piggu_personalizacao_voltou")
        val CORES_DO_CELULAR = booleanPreferencesKey("cores_do_celular")
    }
}
