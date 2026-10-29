package br.com.piggu.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Uma resposta da API guardada do jeito que veio, em JSON (RespostaGuardada do iOS): o resumo e
 * os gastos do mês ("resumo/2026-10", "gastos/2026-10") e as listas para lançar sem sinal
 * ("categorias", "contas", "pessoas-da-familia"). Tudo é da conta e sai com ela.
 */
@Entity(tableName = "respostas_guardadas")
data class RespostaGuardada(
    @PrimaryKey val chave: String,
    val json: String,
    /** Milissegundos desde 1970, para o aviso "mostrando o que estava guardado de...". */
    val guardadaEm: Long,
)

@Dao
interface RespostasGuardadasDao {
    @Query("SELECT * FROM respostas_guardadas WHERE chave = :chave")
    suspend fun ler(chave: String): RespostaGuardada?

    @Upsert
    suspend fun guardar(resposta: RespostaGuardada)

    @Query("DELETE FROM respostas_guardadas")
    suspend fun apagarTudo()
}

@Database(entities = [RespostaGuardada::class], version = 1)
abstract class BancoDoPiggu : RoomDatabase() {
    abstract fun respostas(): RespostasGuardadasDao
}

@Module
@InstallIn(SingletonComponent::class)
object ModuloDoBanco {
    @Provides
    @Singleton
    fun banco(@ApplicationContext contexto: Context): BancoDoPiggu =
        Room.databaseBuilder(contexto, BancoDoPiggu::class.java, "piggu.db")
            // É só cache: numa versão nova do banco, começa vazio em vez de migrar.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun respostas(banco: BancoDoPiggu): RespostasGuardadasDao = banco.respostas()
}
