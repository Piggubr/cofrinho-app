package br.com.piggu.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import br.com.piggu.core.database.BancoDoPiggu
import br.com.piggu.core.model.Gasto
import br.com.piggu.core.model.ItemDeGasto
import br.com.piggu.core.model.NovoLancamento
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.core.testing.DIA_DAS_CAPTURAS
import br.com.piggu.core.testing.RedeDeTeste
import br.com.piggu.core.testing.esperar
import br.com.piggu.core.testing.redeDeTeste
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.math.BigDecimal

/** fila-de-gastos.spec.ts e FilaDeGastosTests.swift, com o Room em memória. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class FilaNoRoomTest {
    @get:Rule val pasta = TemporaryFolder()

    private val contexto: Context = ApplicationProvider.getApplicationContext()
    private val banco = Room.inMemoryDatabaseBuilder(contexto, BancoDoPiggu::class.java).build()

    /** O que o POST /expenses responde, na ordem; acabou a lista, aceita. */
    private val respostas = ArrayDeque<Throwable>()
    private val enviados = mutableListOf<NovoLancamento>()

    @Before fun workManager() = WorkManagerTestInitHelper.initializeTestWorkManager(contexto)
    @After fun fechar() = banco.close()

    private class Montagem(val rede: RedeDeTeste, val fila: FilaNoRoom, val lancamentos: Lancamentos)

    private suspend fun TestScope.montar(): Montagem {
        val rede = redeDeTeste(pasta.root)
        rede.entrar()
        val real = rede.financas
        val financas = object : ServicoDeFinancas by real {
            override suspend fun lancarGastos(pedido: NovoLancamento): List<Gasto> {
                respostas.removeFirstOrNull()?.let { throw it }
                enviados += pedido
                return emptyList()
            }
        }
        val lancamentos = Lancamentos()
        val fila = FilaNoRoom(contexto, banco.gastosAguardando(), financas, rede.sessao, lancamentos, DIA_DAS_CAPTURAS, backgroundScope)
        return Montagem(rede, fila, lancamentos)
    }

    private fun gasto(item: String, id: String = "id-$item") = NovoLancamento(
        data = "2026-10-28",
        itens = listOf(ItemDeGasto(item, valor = BigDecimal("10.00"))),
        idDoEnvio = id,
    )

    private fun servidor(status: Int) = ErroDaApi.DoServidor(status, null, null, emptyList(), null)

    @Test
    fun `envia na ordem e avisa as telas, e guardar agenda o trabalho unico com rede`() = runTest {
        val m = montar()
        m.fila.guardar(gasto("Leite"))
        m.fila.guardar(gasto("Pão"))
        m.fila.guardar(gasto("Café"))
        assertEquals(listOf("id-Leite", "id-Pão", "id-Café"), m.fila.aguardando.esperar { it.size == 3 }.map { it.idDoEnvio })

        val trabalho = WorkManager.getInstance(contexto).getWorkInfosForUniqueWork(ENVIO_DA_FILA).get()
        assertEquals(true, trabalho.isNotEmpty())
        assertEquals(androidx.work.NetworkType.CONNECTED, trabalho.first().constraints.requiredNetworkType)

        assertEquals(3, m.fila.enviar())
        assertEquals(listOf("Leite", "Pão", "Café"), enviados.map { it.itens.single().item })
        assertEquals(1, m.lancamentos.versao.value)
        m.fila.aguardando.esperar { it.isEmpty() }
    }

    @Test
    fun `400 e 422 tiram da fila e contam como recusados`() = runTest {
        val m = montar()
        m.fila.guardar(gasto("Leite"))
        m.fila.guardar(gasto("Pão"))
        m.fila.guardar(gasto("Café"))
        respostas += servidor(400)
        respostas += servidor(422)
        assertEquals(1, m.fila.enviar())
        assertEquals(2, m.fila.recusados.value)
        assertEquals(listOf("Café"), enviados.map { it.itens.single().item })
        assertEquals(0, m.fila.restam())
        m.fila.esquecerRecusados()
        assertEquals(0, m.fila.recusados.value)
    }

    @Test
    fun `500, 401 e sem rede param o envio e guardam o resto`() = runTest {
        val m = montar()
        m.fila.guardar(gasto("Leite"))
        m.fila.guardar(gasto("Pão"))
        for (falha in listOf(servidor(500), servidor(401), ErroDaApi.SemInternet(IOException("sem sinal")))) {
            respostas += falha
            assertEquals(0, m.fila.enviar())
            assertEquals(2, m.fila.restam())
        }
        assertEquals(0, m.fila.recusados.value)
        assertEquals(emptyList<NovoLancamento>(), enviados)
    }

    @Test
    fun `reenvia com o mesmo idDoEnvio`() = runTest {
        val m = montar()
        m.fila.guardar(gasto("Leite", id = "5f0c6f1e-0000-4000-8000-000000000001"))
        respostas += ErroDaApi.SemInternet(IOException("a resposta se perdeu"))
        m.fila.enviar()
        assertEquals(1, m.fila.enviar())
        assertEquals("5f0c6f1e-0000-4000-8000-000000000001", enviados.single().idDoEnvio)
    }

    @Test
    fun `cada pessoa ve e envia so os seus, e sair nao apaga`() = runTest {
        val m = montar()
        val ana = m.rede.sessao.usuario!!
        m.fila.guardar(gasto("Da Ana"))
        m.rede.sessao.entrou(ana.copy(id = "u-bia"))
        m.fila.guardar(gasto("Da Bia"))
        m.fila.aguardando.esperar { lista -> lista.map { it.idDoEnvio } == listOf("id-Da Bia") }

        m.rede.sessao.entrou(ana)
        m.fila.aguardando.esperar { lista -> lista.map { it.idDoEnvio } == listOf("id-Da Ana") }
        assertEquals(1, m.fila.enviar())
        assertEquals(listOf("Da Ana"), enviados.map { it.itens.single().item })
    }

    @Test
    fun `sessao que so caiu nao apaga a fila`() = runTest {
        val m = montar()
        val ana = m.rede.sessao.usuario!!
        m.fila.guardar(gasto("Leite"))
        m.rede.sessao.encerrarLocalmente()
        m.fila.aguardando.esperar { it.isEmpty() }
        m.rede.sessao.entrou(ana)
        assertEquals(1, m.fila.restam())
    }

    @Test
    fun `o worker envia e so tenta de novo quando sobrou gasto`() = runTest {
        val m = montar()
        m.fila.guardar(gasto("Leite"))
        m.fila.guardar(gasto("Pão"))
        val fabrica = object : WorkerFactory() {
            override fun createWorker(contexto: Context, classe: String, parametros: WorkerParameters): ListenableWorker =
                EnvioDaFilaWorker(contexto, parametros, m.fila, m.rede.sessao)
        }
        fun worker() = TestListenableWorkerBuilder<EnvioDaFilaWorker>(contexto).setWorkerFactory(fabrica).build()

        respostas += servidor(503)
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(2, enviados.size)
    }
}
