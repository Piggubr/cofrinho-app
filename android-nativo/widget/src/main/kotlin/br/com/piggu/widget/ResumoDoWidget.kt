package br.com.piggu.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.piggu.core.model.Decimal
import br.com.piggu.core.model.JsonDoPiggu
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Singleton

/**
 * O que os widgets mostram, gravado pelo app ao abrir, ao voltar e depois de lançar um gasto
 * (ResumoDoWidget.swift do iOS, D-093). Só números do mês: nunca token, e-mail ou lista de gastos.
 */
@Serializable
data class ResumoDoWidget(
    /** aaaa-mm */
    val mes: String,
    val gasto: Decimal,
    /** O limite do mês (Orçamento); nulo sem limite. */
    val limite: Decimal? = null,
    /** ISO 4217, a moeda da pessoa. */
    val moeda: String? = null,
    /** A primeira meta do mês ainda aberta (ou a última, se todas acabaram). */
    val meta: Meta? = null,
    val metasConcluidas: Int = 0,
    val metasTotal: Int = 0,
    /** A cor do Piggu que vale (VarianteDeCor.id); nula com as Cores do celular. */
    val cor: String? = null,
    val atualizadoEm: Long,
) {
    @Serializable
    data class Meta(val titulo: String, val atual: Int, val alvo: Int)

    /** Quanto do limite já foi, em %, de 0 em diante (passa de 100); nulo sem limite. */
    val porcentagemDoLimite: Int?
        get() = limite?.takeIf { it.signum() > 0 }
            ?.let { gasto.multiply(BigDecimal(100)).divide(it, 0, RoundingMode.HALF_UP).toInt() }

    val restante: BigDecimal? get() = limite?.subtract(gasto)
}

/** O resumo num DataStore só dele, que o widget lê fora do app (o app pode estar fechado). */
class GuardaDoResumo(private val dados: DataStore<Preferences>) {

    val resumo: Flow<ResumoDoWidget?> = dados.data.map { prefs ->
        prefs[CHAVE]?.let { runCatching { JsonDoPiggu.decodeFromString(ResumoDoWidget.serializer(), it) }.getOrNull() }
    }

    suspend fun gravar(resumo: ResumoDoWidget) {
        dados.edit { it[CHAVE] = JsonDoPiggu.encodeToString(ResumoDoWidget.serializer(), resumo) }
    }

    /** Ao sair da conta: os números são da pessoa, e o aparelho pode ser de outra depois. */
    suspend fun apagar() {
        dados.edit { it.remove(CHAVE) }
    }

    companion object {
        private val CHAVE = stringPreferencesKey("resumo-do-widget")

        /** Para o widget, que roda sem o Hilt; o DataStore é um só por processo. */
        fun de(contexto: Context) = GuardaDoResumo(contexto.applicationContext.resumoDoWidget)
    }
}

private val Context.resumoDoWidget: DataStore<Preferences> by preferencesDataStore("widget")

@Module
@InstallIn(SingletonComponent::class)
object ModuloDoWidget {
    @Provides
    @Singleton
    fun guardaDoResumo(@ApplicationContext contexto: Context): GuardaDoResumo = GuardaDoResumo.de(contexto)
}
