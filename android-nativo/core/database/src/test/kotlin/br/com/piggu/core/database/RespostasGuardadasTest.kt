package br.com.piggu.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class RespostasGuardadasTest {
    private val banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BancoDoPiggu::class.java)
        .allowMainThreadQueries()
        .build()
    private val respostas = banco.respostas()

    @After
    fun fechar() = banco.close()

    @Test
    fun `guardar de novo troca a resposta e a hora`() = runTest {
        respostas.guardar(RespostaGuardada("resumo/2026-10", "{\"a\":1}", 1))
        respostas.guardar(RespostaGuardada("resumo/2026-10", "{\"a\":2}", 2))
        assertEquals(RespostaGuardada("resumo/2026-10", "{\"a\":2}", 2), respostas.ler("resumo/2026-10"))
    }

    @Test
    fun `apagar tudo ao sair da conta`() = runTest {
        respostas.guardar(RespostaGuardada("categorias", "[]", 1))
        respostas.apagarTudo()
        assertNull(respostas.ler("categorias"))
    }
}
