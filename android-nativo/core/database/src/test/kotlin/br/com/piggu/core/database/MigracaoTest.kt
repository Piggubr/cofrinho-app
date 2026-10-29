package br.com.piggu.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class MigracaoTest {
    @get:Rule
    val ajudante = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BancoDoPiggu::class.java)

    @Test
    fun `da versao 1 para a 2 cria a fila e mantem o cache`() {
        ajudante.createDatabase("migracao.db", 1).use { banco ->
            banco.execSQL("INSERT INTO respostas_guardadas (chave, json, guardadaEm) VALUES ('categorias', '[]', 1)")
        }
        ajudante.runMigrationsAndValidate("migracao.db", 2, true).use { banco ->
            banco.query("SELECT COUNT(*) FROM respostas_guardadas").use { assertEquals(1, it.apply { moveToFirst() }.getInt(0)) }
            banco.execSQL("INSERT INTO gastos_aguardando (usuario, idDoEnvio, json, guardadoEm) VALUES ('u-ana', 'id-1', '{}', 1)")
            banco.query("SELECT COUNT(*) FROM gastos_aguardando").use { assertEquals(1, it.apply { moveToFirst() }.getInt(0)) }
        }
    }
}
