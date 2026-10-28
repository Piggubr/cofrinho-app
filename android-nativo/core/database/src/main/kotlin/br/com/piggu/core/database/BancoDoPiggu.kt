package br.com.piggu.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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
import kotlinx.coroutines.flow.Flow
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

/**
 * Um gasto lançado sem internet, esperando subir (fila-de-gastos.ts). Não é cache: é gasto que o
 * servidor ainda não tem, e só sai quando sobe, quando o servidor recusa (400 ou 422) ou quando a
 * pessoa sai pelo botão. [ordem] é a ordem em que foi lançado.
 */
@Entity(tableName = "gastos_aguardando", indices = [Index("usuario"), Index("idDoEnvio", unique = true)])
data class GastoGuardado(
    @PrimaryKey(autoGenerate = true) val ordem: Long = 0,
    /** Id de quem lançou: só essa pessoa envia, mesmo que outra entre no aparelho. */
    val usuario: String,
    val idDoEnvio: String,
    /** O NovoLancamento em JSON, do jeito que vai para a API. */
    val json: String,
    /** Milissegundos desde 1970. */
    val guardadoEm: Long,
)

@Dao
interface GastosAguardandoDao {
    @Query("SELECT * FROM gastos_aguardando WHERE usuario = :usuario ORDER BY ordem")
    fun observar(usuario: String): Flow<List<GastoGuardado>>

    @Query("SELECT * FROM gastos_aguardando WHERE usuario = :usuario ORDER BY ordem")
    suspend fun daPessoa(usuario: String): List<GastoGuardado>

    /** O mesmo idDoEnvio duas vezes fica uma só. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun guardar(gasto: GastoGuardado)

    @Query("DELETE FROM gastos_aguardando WHERE ordem = :ordem")
    suspend fun tirar(ordem: Long)

    @Query("DELETE FROM gastos_aguardando WHERE usuario = :usuario")
    suspend fun apagarDaPessoa(usuario: String)
}

/**
 * Versão 2: a fila de gastos. Ela não é cache, então toda versão nova precisa de migração (aqui
 * automática) para não perder gasto guardado; só o resto pode começar vazio.
 */
@Database(
    entities = [RespostaGuardada::class, GastoGuardado::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class BancoDoPiggu : RoomDatabase() {
    abstract fun respostas(): RespostasGuardadasDao
    abstract fun gastosAguardando(): GastosAguardandoDao
}

@Module
@InstallIn(SingletonComponent::class)
object ModuloDoBanco {
    @Provides
    @Singleton
    fun banco(@ApplicationContext contexto: Context): BancoDoPiggu =
        Room.databaseBuilder(contexto, BancoDoPiggu::class.java, "piggu.db")
            // Versão sem migração começa vazia. A fila de gastos não é cache: cada versão nova
            // traz a sua migração (a 1 para a 2 é automática).
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun respostas(banco: BancoDoPiggu): RespostasGuardadasDao = banco.respostas()

    @Provides
    fun gastosAguardando(banco: BancoDoPiggu): GastosAguardandoDao = banco.gastosAguardando()
}
