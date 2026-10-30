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
import kotlinx.coroutines.flow.first
import javax.inject.Singleton

/**
 * A conversa com o assistente, guardada só neste aparelho (conversa-do-assistente.ts do site): o
 * JSON que o `feature:assistente` monta, com o dono e as falas. Uma por aparelho, da última pessoa
 * que conversou; o prazo de 30 dias e o teto de falas ficam com quem monta o JSON.
 */
class ConversaGuardada(private val dados: DataStore<Preferences>) {

    suspend fun ler(): String? = dados.data.first()[CHAVE]

    suspend fun guardar(json: String) {
        dados.edit { it[CHAVE] = json }
    }

    /**
     * Apaga a conversa do aparelho. O app chama ao sair da conta: ela traz números da família, e o
     * aparelho pode ser de outra pessoa depois.
     */
    suspend fun esquecerConversa() {
        dados.edit { it.remove(CHAVE) }
    }

    private companion object {
        // O mesmo nome do localStorage do site.
        val CHAVE = stringPreferencesKey("piggu_conversa_do_assistente")
    }
}

private val Context.assistente: DataStore<Preferences> by preferencesDataStore("assistente")

@Module
@InstallIn(SingletonComponent::class)
object ModuloDaConversa {
    @Provides
    @Singleton
    fun conversaGuardada(@ApplicationContext contexto: Context): ConversaGuardada = ConversaGuardada(contexto.assistente)
}
