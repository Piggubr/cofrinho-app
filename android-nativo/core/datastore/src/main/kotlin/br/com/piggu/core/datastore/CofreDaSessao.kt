package br.com.piggu.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.Base64

/**
 * A sessão guardada no aparelho: o refresh, cifrado, e o perfil (o JSON do /api/auth/me), para o
 * app abrir logado na hora (D-095 do iOS). O access token nunca vem para cá: fica só na memória.
 *
 * <p>Refresh que não decifra (a chave do Keystore sumiu, por exemplo depois de restaurar o
 * aparelho) vale como sessão nenhuma: a pessoa entra de novo.</p>
 */
class CofreDaSessao(
    private val dados: DataStore<Preferences>,
    private val cifra: Cifra,
) {
    suspend fun refresh(): String? {
        val guardado = dados.data.first()[REFRESH] ?: return null
        return runCatching { String(cifra.decifrar(Base64.getDecoder().decode(guardado)), Charsets.UTF_8) }.getOrNull()
    }

    suspend fun perfil(): String? = dados.data.first()[PERFIL]

    /** Nulo apaga. */
    suspend fun guardarRefresh(refresh: String?) {
        dados.edit {
            if (refresh == null) it.remove(REFRESH)
            else it[REFRESH] = Base64.getEncoder().encodeToString(cifra.cifrar(refresh.toByteArray(Charsets.UTF_8)))
        }
    }

    /** Nulo apaga. */
    suspend fun guardarPerfil(json: String?) {
        dados.edit { if (json == null) it.remove(PERFIL) else it[PERFIL] = json }
    }

    suspend fun apagar() {
        dados.edit { it.remove(REFRESH); it.remove(PERFIL) }
    }

    private companion object {
        val REFRESH = stringPreferencesKey("refresh")
        val PERFIL = stringPreferencesKey("perfil")
    }
}
